import * as THREE from './three.module.js';
import { SERVICES, GROUPS, SCENE_THEMES, getRoomProfile } from './campus-data.js';

/** The spatial layer owns movement and geometry. Service content stays in the app. */
export async function createCampusScene({ container, labels, services = SERVICES, onEnter, onRoomInteract, onNavigate, onPosition, onError } = {}) {
  if (!container || !labels) throw new Error('缺少校园场景容器');

  const renderer = new THREE.WebGLRenderer({ antialias: true, alpha: false, powerPreference: 'high-performance' });
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 1.7));
  renderer.outputColorSpace = THREE.SRGBColorSpace;
  renderer.toneMapping = THREE.ACESFilmicToneMapping;
  renderer.toneMappingExposure = 1.1;
  renderer.shadowMap.enabled = true;
  renderer.shadowMap.type = THREE.PCFSoftShadowMap;
  const canvas = renderer.domElement;
  canvas.className = 'campus-canvas';
  canvas.tabIndex = 0;
  canvas.setAttribute('aria-label', '校园三维校园。拖动环视，W A S D 行走，点击服务门进入房间，再点击服务台或屏幕打开内容。');
  Object.assign(canvas.style, { display: 'block', width: '100%', height: '100%', touchAction: 'none', outlineOffset: '-4px' });
  container.appendChild(canvas);
  labels.removeAttribute('aria-hidden');

  const scene = new THREE.Scene();
  scene.background = new THREE.Color(0xe4f1fc);
  scene.fog = new THREE.Fog(0xe4f1fc, 43, 110);
  const camera = new THREE.PerspectiveCamera(66, 1, 0.08, 150);
  const V = (x = 0, y = 0, z = 0) => new THREE.Vector3(x, y, z);
  const UP = V(0, 1, 0);
  const EYE = 2.05;
  const HOME = V(7.3, EYE, 9.3);
  const HOME_YAW = 0.43;
  const HALF_HALL = 11;
  const COR_HALF = 4.15;
  const ROOM_DEPTH = 8.6;
  const ROOM_HEIGHT = 5.7;
  const END = -35;
  const CORRIDORS = [
    { id: 'service', ...SCENE_THEMES.service, name: '校园服务', caption: '活动 · 闲置 · 搭子 · 失物', rot: 0, color: '#2864ea' },
    { id: 'social', ...SCENE_THEMES.social, name: '社交信息', caption: '问答 · 动态 · 公告 · 消息', rot: Math.PI / 2, color: '#1b9e95' },
    { id: 'ai', ...SCENE_THEMES.ai, name: 'AI 学习', caption: '答疑 · 代码 · 错题', rot: -Math.PI / 2, color: '#8470cf' }
  ];
  const serviceMap = new Map(services.map(item => [item.id, item]));
  const groupArray = Array.isArray(GROUPS) ? GROUPS : Object.values(GROUPS || {});
  const walls = [];
  const pickables = [];
  const doors = new Map();
  const labelRecords = [];
  const disposables = new Set();
  const listeners = [];
  const leaves = [];
  const landscapeObjects = [];
  const glowSources = [];
  const material = (color, options = {}) => {
    const value = new THREE.MeshStandardMaterial({ color, roughness: 0.72, metalness: 0.04, ...options });
    disposables.add(value);
    return value;
  };
  const M = {
    white: material(0xf8fbff), wall: material(0xecf3fa), floor: material(0xd9e8f3, { roughness: 0.33 }),
    trim: material(0xaabed0, { roughness: 0.32, metalness: 0.55 }), pale: material(0xe8f2fc),
    blue: material(0x3478ed), dark: material(0x193e66), wood: material(0xa4b19d),
    soil: material(0x536954), bark: material(0x947c61), leaf: material(0x479d68), leafLight: material(0x75b773),
    glass: material(0xb9dff5, { transparent: true, opacity: 0.22, roughness: 0.1, metalness: 0.14, depthWrite: false, side: THREE.DoubleSide }),
    window: material(0xa6d7f4, { transparent: true, opacity: 0.31, roughness: 0.11, depthWrite: false, side: THREE.DoubleSide }),
    light: material(0xffffff, { emissive: 0xe9f5ff, emissiveIntensity: 0.7 }),
    seam: material(0xbecfdf), lawn: material(0x94c9ad)
  };

  function mesh(geometry, mat, parent, x = 0, y = 0, z = 0, cast = false) {
    disposables.add(geometry);
    const object = new THREE.Mesh(geometry, mat);
    object.position.set(x, y, z);
    object.castShadow = cast;
    object.receiveShadow = true;
    parent.add(object);
    return object;
  }
  function box(parent, w, h, d, mat, x = 0, y = 0, z = 0, cast = false, solid = false) {
    const object = mesh(new THREE.BoxGeometry(w, h, d), mat, parent, x, y, z, cast);
    if (solid) walls.push(object);
    return object;
  }
  function cylinder(parent, top, bottom, height, mat, x = 0, y = 0, z = 0, cast = false, segments = 40) {
    return mesh(new THREE.CylinderGeometry(top, bottom, height, segments), mat, parent, x, y, z, cast);
  }
  function sphere(parent, radius, mat, x, y, z, sx = 1, sy = 1, sz = 1, cast = true) {
    const object = mesh(new THREE.IcosahedronGeometry(radius, 2), mat, parent, x, y, z, cast);
    object.scale.set(sx, sy, sz);
    return object;
  }
  function branch(parent, from, to, bottom, top, mat) {
    const vector = to.clone().sub(from);
    const object = cylinder(parent, top, bottom, vector.length(), mat, 0, 0, 0, true, 9);
    object.position.copy(from).add(to).multiplyScalar(0.5);
    object.quaternion.setFromUnitVectors(UP, vector.normalize());
    return object;
  }
  function textureCanvas(width, height, paint) {
    const element = document.createElement('canvas');
    element.width = width;
    element.height = height;
    paint(element.getContext('2d'), width, height);
    const texture = new THREE.CanvasTexture(element);
    texture.colorSpace = THREE.SRGBColorSpace;
    texture.anisotropy = Math.min(renderer.capabilities.getMaxAnisotropy(), 4);
    disposables.add(texture);
    return texture;
  }
  function textBoard(parent, text, sub, color, w, h, x, y, z, rotation = 0, overlay = false) {
    const texture = textureCanvas(1024, 320, (ctx, width, height) => {
      if (overlay) {
        // 覆盖提示层：透明底 + 底部深色条带，海报替换材质后仍保持名称与“点击屏幕打开服务内容”可读
        ctx.clearRect(0, 0, width, height);
        ctx.fillStyle = 'rgba(10, 22, 38, 0.68)'; ctx.fillRect(0, height - 96, width, 96);
        ctx.fillStyle = '#ffffff'; ctx.font = '600 52px "Microsoft YaHei", sans-serif';
        ctx.textBaseline = 'middle'; ctx.fillText(text, 30, height - 58);
        ctx.fillStyle = 'rgba(255,255,255,0.88)'; ctx.font = '26px "Microsoft YaHei", sans-serif';
        ctx.fillText(sub, 32, height - 22);
      } else {
        ctx.fillStyle = '#f7fbff'; ctx.fillRect(0, 0, width, height);
        ctx.fillStyle = color; ctx.fillRect(0, 0, 13, height);
        ctx.fillStyle = '#254564'; ctx.font = '600 70px "Microsoft YaHei", sans-serif';
        ctx.textBaseline = 'middle'; ctx.fillText(text, 57, 112);
        ctx.fillStyle = '#6c8298'; ctx.font = '30px "Microsoft YaHei", sans-serif'; ctx.fillText(sub, 60, 224);
      }
    });
    const mat = new THREE.MeshBasicMaterial({ map: texture, side: THREE.DoubleSide, transparent: overlay, depthWrite: !overlay });
    disposables.add(mat);
    const board = mesh(new THREE.PlaneGeometry(w, h), mat, parent, x, y, z);
    board.rotation.y = rotation;
    board.userData.textMaterial = mat;
    return board;
  }
  function roomLight(parent, color, z = -3.6) {
    box(parent, 3.1, 0.07, 0.52, M.light, 0, 5.31, z);
    box(parent, 1.7, 0.035, 0.56, material(color, { emissive: color, emissiveIntensity: 0.45 }), 0, 5.26, z);
    const light = new THREE.PointLight(color, 1.1, 9, 2);
    light.position.set(0, 4.78, z);
    parent.add(light);
  }
  function roomProp(parent, label, color, x, z, variant = 'shelf', serviceId = null) {
    const isDesk = variant === 'desk';
    const width = isDesk ? 2.2 : 1.35;
    const depth = isDesk ? 0.76 : 0.62;
    const height = isDesk ? 0.86 : 1.65;
    const top = box(parent, width, 0.12, depth, M.white, x, height, z, true);
    if (isDesk && serviceId) { top.userData.serviceId = serviceId; top.userData.hotspot = true; pickables.push(top); }
    box(parent, width, height, 0.1, M.pale, x, height / 2, z - depth / 2 + 0.06, true);
    box(parent, width - 0.12, 0.07, 0.1, material(color, { roughness: 0.42, metalness: 0.08 }), x, height - 0.12, z - depth / 2 - 0.01);
    if (!isDesk) {
      box(parent, width - 0.2, 0.06, 0.08, M.trim, x, height * 0.56, z - depth / 2 - 0.01);
      box(parent, width - 0.2, 0.06, 0.08, M.trim, x, height * 0.27, z - depth / 2 - 0.01);
    }
    // 房间内不再为货架/服务台悬挂额外指示牌：房门牌已含房间名称与编号，
    // 屏幕自带“点击屏幕打开服务内容”交互提示，避免重复悬浮牌遮挡与穿模。
  }
  function roomScreen(parent, service, color, z) {
    const screen = box(parent, 2.6, 1.46, 0.13, M.dark, 0, 2.12, z, true);
    screen.userData.serviceId = service.id;
    screen.userData.hotspot = true;
    pickables.push(screen);
    glowBox(parent, 2.76, 0.055, 0.07, color, 0, 2.86, z + 0.08, false, 0.62);
    glowBox(parent, 2.76, 0.055, 0.07, color, 0, 1.38, z + 0.08, false, 0.48);
    glowBox(parent, 0.055, 1.42, 0.07, color, -1.35, 2.12, z + 0.08, false, 0.48);
    glowBox(parent, 0.055, 1.42, 0.07, color, 1.35, 2.12, z + 0.08, false, 0.48);
    const board = textBoard(parent, service.name, '点击屏幕打开服务内容', color, 2.43, 1.22, 0, 2.12, z + 0.075);
    if (service.image) {
      const loader = new THREE.TextureLoader();
      loader.load(service.image, texture => {
        if (disposed) { texture.dispose(); return; }
        texture.colorSpace = THREE.SRGBColorSpace;
        texture.anisotropy = Math.min(renderer.capabilities.getMaxAnisotropy(), 4);
        const targetAspect = 2;
        const imageAspect = (texture.image?.width || 1) / (texture.image?.height || 1);
        if (imageAspect > targetAspect) {
          const repeatX = targetAspect / imageAspect;
          texture.repeat.set(repeatX, 1);
          texture.offset.set((1 - repeatX) / 2, 0);
        } else {
          const repeatY = imageAspect / targetAspect;
          texture.repeat.set(1, repeatY);
          texture.offset.set(0, (1 - repeatY) / 2);
        }
        texture.needsUpdate = true;
        const imageMaterial = new THREE.MeshBasicMaterial({ map: texture, side: THREE.DoubleSide });
        disposables.add(texture);
        disposables.add(imageMaterial);
        board.material = imageMaterial;
        board.userData.imageMaterial = imageMaterial;
        // 第9项修复：海报不遮提示——叠加半透明深色条带提示层，房间名称与“点击屏幕打开服务内容”始终可读
        const caption = textBoard(parent, service.name, '点击屏幕打开服务内容', color, 2.43, 1.22, 0, 2.12, z + 0.075 + 0.015, 0, true);
        caption.renderOrder = 10;
      }, undefined, () => {
        // The text board is deliberately kept visible when a generated asset fails to load.
      });
    }
    box(parent, 1.05, 0.12, 0.42, M.white, 0, 0.94, z + 0.26, true);
    box(parent, 0.08, 0.78, 0.35, M.trim, -0.42, 0.47, z + 0.24, true);
    box(parent, 0.08, 0.78, 0.35, M.trim, 0.42, 0.47, z + 0.24, true);
    return screen;
  }
  function floor(parent, width, depth, x, z, color = M.floor) {
    box(parent, width, 0.18, depth, color, x, -0.1, z);
    const tile = 2.4;
    for (let tx = -width / 2 + tile; tx < width / 2; tx += tile) box(parent, 0.016, 0.006, depth, M.seam, x + tx, 0.003, z);
    for (let tz = -depth / 2 + tile; tz < depth / 2; tz += tile) box(parent, width, 0.006, 0.016, M.seam, x, 0.003, z + tz);
  }
  function planter(parent, x, z, scale = 1) {
    cylinder(parent, 0.52 * scale, 0.38 * scale, 0.8 * scale, M.white, x, 0.4 * scale, z, true);
    cylinder(parent, 0.47 * scale, 0.47 * scale, 0.025, M.soil, x, 0.805 * scale, z);
    for (let index = 0; index < 6; index++) {
      const a = index * Math.PI / 3;
      const object = sphere(parent, 0.43 * scale, index % 2 ? M.leaf : M.leafLight, x + Math.sin(a) * 0.2 * scale, 1.18 * scale, z + Math.cos(a) * 0.2 * scale, 0.7, 1.5, 0.6);
      object.rotation.z = Math.sin(a) * 0.36;
    }
  }
  function glowBox(parent, w, h, d, color, x = 0, y = 0, z = 0, cast = false, base = 0.55) {
    const glowMaterial = material(color, { emissive: color, emissiveIntensity: base, roughness: 0.38, metalness: 0.08 });
    const object = box(parent, w, h, d, glowMaterial, x, y, z, cast);
    glowSources.push({ material: glowMaterial, base, phase: glowSources.length * 0.73 });
    return object;
  }
  function ringLight(parent, radius, color, y = 0.035) {
    const ring = mesh(new THREE.RingGeometry(radius - 0.045, radius, 72), material(color, { emissive: color, emissiveIntensity: 0.42, roughness: 0.3 }), parent, 0, y, 0);
    ring.rotation.x = -Math.PI / 2;
    glowSources.push({ material: ring.material, base: 0.42, phase: glowSources.length * 0.51 });
    return ring;
  }
  function infoKiosk(parent, color, x, z) {
    box(parent, 1.42, 0.18, 0.92, M.white, x, 1.48, z, true);
    box(parent, 0.92, 1.45, 0.1, M.dark, x, 0.78, z - 0.36, true);
    glowBox(parent, 0.72, 0.06, 0.04, color, x, 1.25, z - 0.305, false, 0.65);
    cylinder(parent, 0.14, 0.19, 0.18, M.trim, x, 0.09, z, true, 24);
  }
  function seatTable(parent, x, z, rotation = 0) {
    const group = new THREE.Group(); group.position.set(x, 0, z); group.rotation.y = rotation; parent.add(group);
    cylinder(group, 0.46, 0.52, 0.08, M.wood, 0, 0.86, 0, true, 28);
    cylinder(group, 0.08, 0.11, 0.8, M.trim, 0, 0.43, 0, true, 18);
    for (const side of [-1, 1]) {
      box(group, 0.55, 0.08, 0.34, M.white, side * 0.72, 0.45, 0, true);
      box(group, 0.08, 0.42, 0.28, M.trim, side * 0.72, 0.23, 0, true);
    }
  }
  function ceilingStrip(parent, color, z) {
    box(parent, 2.5, 0.06, 0.34, M.white, -2.05, 5.16, z);
    glowBox(parent, 1.85, 0.035, 0.22, color, -2.05, 5.11, z, false, 0.52);
    box(parent, 2.5, 0.06, 0.34, M.white, 2.05, 5.16, z);
    glowBox(parent, 1.85, 0.035, 0.22, color, 2.05, 5.11, z, false, 0.52);
  }
  function wayfindingArrow(parent, color, z) {
    glowBox(parent, 1.28, 0.025, 0.07, color, 0, 0.035, z, false, 0.42);
    glowBox(parent, 0.07, 0.025, 0.62, color, -0.59, 0.035, z - 0.28, false, 0.42);
    glowBox(parent, 0.07, 0.025, 0.62, color, 0.59, 0.035, z - 0.28, false, 0.42);
  }

  // Daylight, a restrained blue skylight and real contact shadows make the geometry legible.
  const hemi = new THREE.HemisphereLight(0xe4f3ff, 0xa0afb9, 2.3);
  scene.add(hemi);
  const sun = new THREE.DirectionalLight(0xfffbf2, 2.7);
  sun.position.set(-12, 26, 13);
  sun.castShadow = true;
  sun.shadow.mapSize.set(2048, 2048);
  Object.assign(sun.shadow.camera, { left: -40, right: 40, top: 40, bottom: -40, near: 0.5, far: 90 });
  sun.shadow.normalBias = 0.035;
  sun.shadow.bias = -0.0001;
  sun.shadow.radius = 4;
  scene.add(sun);
  const fill = new THREE.DirectionalLight(0xb7dcff, 0.75);
  fill.position.set(18, 8, -18);
  scene.add(fill);

  // The open courtyard has three corridor entrances and glazed corners.
  const hall = new THREE.Group();
  scene.add(hall);
  floor(hall, 22.5, 22.5, 0, 0);
  const ring = mesh(new THREE.RingGeometry(3.15, 3.24, 80), M.blue, hall, 0, 0.016, 0);
  ring.rotation.x = -Math.PI / 2;
  ringLight(hall, 3.48, SCENE_THEMES.hub.accent, 0.028);
  ringLight(hall, 4.08, SCENE_THEMES.hub.soft, 0.022);
  for (const [x, z] of [[-5.9, 3.8], [5.9, 3.8]]) infoKiosk(hall, SCENE_THEMES.hub.accent, x, z);
  seatTable(hall, -6.8, -1.6, 0.2);
  seatTable(hall, 6.8, -1.6, -0.2);
  for (const [x, z, color] of [[-7.3, -8.9, SCENE_THEMES.service.accent], [7.3, -8.9, SCENE_THEMES.social.accent], [0, -9.9, SCENE_THEMES.ai.accent]]) {
    const marker = ringLight(hall, 0.62, color, 0.035);
    marker.position.set(x, 0, z);
  }
  for (const side of [-1, 1]) {
    for (const segment of [-1, 1]) {
      const center = segment * 7.55;
      box(hall, 0.28, 1.02, 6.1, M.white, side * HALF_HALL, 0.51, center, true, true);
      box(hall, 0.06, 5.3, 6.1, M.window, side * HALF_HALL, 3.7, center);
      box(hall, 0.36, 0.36, 6.6, M.white, side * HALF_HALL, 6.54, center, true);
      for (const offset of [-2.85, 0, 2.85]) box(hall, 0.18, 6.5, 0.15, M.trim, side * HALF_HALL, 3.25, center + offset, true);
    }
    box(hall, 6.1, 1.02, 0.28, M.white, side * 7.55, 0.51, -HALF_HALL, true, true);
    box(hall, 6.1, 5.3, 0.06, M.window, side * 7.55, 3.7, -HALF_HALL);
    for (const offset of [-2.85, 0, 2.85]) box(hall, 0.15, 6.5, 0.18, M.trim, side * 7.55 + offset, 3.25, -HALF_HALL, true);
    box(hall, 6.6, 0.36, 0.36, M.white, side * 7.55, 6.54, -HALF_HALL, true);
  }
  // Rear glazing keeps the initial view light and lets visitors turn around naturally.
  box(hall, 22.2, 0.9, 0.25, M.white, 0, 0.45, HALF_HALL + 0.25, true, true);
  box(hall, 22.2, 5.7, 0.06, M.window, 0, 3.75, HALF_HALL + 0.25);
  for (let x = -11; x <= 11; x += 3.65) box(hall, 0.18, 6.7, 0.18, M.trim, x, 3.35, HALF_HALL + 0.25);
  // A high, clear skylight: only the slim mullions cast shadows.
  box(hall, 22.4, 0.06, 22.4, M.glass, 0, 8.5, 0);
  for (const x of [-11, -5.5, 0, 5.5, 11]) box(hall, 0.13, 0.2, 22.4, M.white, x, 8.52, 0, true);
  for (const z of [-11, -5.5, 0, 5.5, 11]) box(hall, 22.4, 0.2, 0.13, M.white, 0, 8.52, z, true);
  for (const x of [-10.8, 10.8]) for (const z of [-10.8, 10.8]) box(hall, 0.32, 8.5, 0.32, M.white, x, 4.25, z, true);

  // Central plane tree, set in a low white planter with an oak seating ring.
  const tree = new THREE.Group();
  hall.add(tree);
  const planterBase = cylinder(tree, 2.22, 2.36, 0.5, M.white, 0, 0.25, 0, true, 80);
  planterBase.userData.serviceId = 'portal'; pickables.push(planterBase);
  cylinder(tree, 2.15, 2.15, 0.04, M.soil, 0, 0.53, 0, false, 80);
  const bench = mesh(new THREE.RingGeometry(2.27, 2.88, 80), M.wood, tree, 0, 0.57, 0);
  bench.rotation.x = -Math.PI / 2;
  cylinder(tree, 2.85, 2.85, 0.14, M.wood, 0, 0.47, 0, true, 80);
  cylinder(tree, 2.28, 2.28, 0.16, M.soil, 0, 0.55, 0, false, 80);
  branch(tree, V(0, 0.56, 0), V(0.12, 4.25, 0.08), 0.43, 0.22, M.bark);
  const crowns = [
    [-1.85, 5.25, 0.25, 1.65], [1.75, 5.35, 0.5, 1.6], [-0.5, 6.4, -0.35, 1.85],
    [0.5, 5.3, -1.85, 1.55], [-0.9, 5.2, 1.65, 1.65], [1.8, 6.3, -0.9, 1.15], [-2, 6.1, -0.8, 1.1]
  ];
  crowns.forEach(([x, y, z, radius], index) => {
    branch(tree, V(0.1, 2.7 + (index % 3) * 0.35, 0), V(x * 0.86, y - 0.5, z * 0.86), 0.2, 0.075, M.bark);
    const crown = sphere(tree, radius, index % 2 ? M.leafLight : M.leaf, x, y, z, 1, 0.81, 1);
    leaves.push({ mesh: crown, rotation: crown.rotation.z, offset: index * 1.7 });
  });
  for (const [x, z] of [[-2.2, -1.1], [2.1, -1.4], [-1.3, 2.1], [1.45, 1.8]]) {
    branch(tree, V(x * 0.08, 1.9, z * 0.08), V(x * 0.24, 3.15, z * 0.24), 0.12, 0.045, M.bark);
  }
  glowBox(tree, 3.8, 0.035, 0.08, SCENE_THEMES.hub.accent, 0, 0.72, 0, false, 0.38);
  textBoard(hall, '梧桐校园', '一站式校园服务中心', '#3478ed', 2.18, 0.68, 0, 1.05, 2.28);
  planter(hall, -8.8, -8.8, 1.2);
  planter(hall, 8.8, -8.8, 1.2);
  planter(hall, -8.7, 7.6, 1.0);

  // Exterior landscape is visible through the glass without loading remote assets.
  box(scene, 105, 0.2, 105, M.lawn, 0, -0.29, 0);
  // Keep background greenery beyond the service-door sightlines. The courtyard
  // tree and planters provide the close greenery; these distant trees only frame
  // the glass exterior and must not appear to grow inside a room.
  for (const [x, z, s] of [[-39, -28, 1.0], [39, -28, 0.95], [-39, 28, 1.1], [39, 28, 1.0], [-48, 8, 0.9], [48, 12, 1.0]]) {
    const trunk = cylinder(scene, 0.17, 0.25, 3.4 * s, M.bark, x, 1.7 * s, z, true, 8);
    const crown = sphere(scene, 2.2 * s, M.leafLight, x, 4 * s, z, 0.85, 1.1, 0.85);
    landscapeObjects.push(trunk, crown);
  }
  for (const [x, z, w, h, d] of [[-30, 23, 17, 10, 9], [29, 23, 18, 13, 8], [0, 31, 20, 8, 9]]) {
    box(scene, w, h, d, M.pale, x, h / 2, z);
    for (let row = 2; row < h; row += 2.1) box(scene, w + 0.02, 0.95, d + 0.02, M.window, x, row, z);
  }

  function toWorld(local, corridor) { return local.clone().applyAxisAngle(UP, corridor.rot); }
  function localOf(point, corridor) { return point.clone().applyAxisAngle(UP, -corridor.rot); }
  function makeLabel(id, name, room, anchor, group, isArch = false) {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = isArch ? 'scene-arch-label' : 'scene-door-label';
    button.setAttribute('aria-label', isArch ? `前往${name}` : `进入${name}`);
    button.dataset.service = id;
    const dot = document.createElement('span'); dot.className = 'scene-label-dot';
    const color = CORRIDORS.find(item => item.id === group)?.color || '#3478ed';
    dot.style.backgroundColor = color;
    const title = document.createElement('span'); title.className = 'scene-label-name'; title.textContent = name;
    button.append(dot, title);
    if (room) { const detail = document.createElement('span'); detail.className = 'scene-label-room'; detail.textContent = room; button.append(detail); }
    Object.assign(button.style, { position: 'absolute', left: '0', top: '0', transform: 'translate(-50%, -100%)', pointerEvents: 'auto', whiteSpace: 'nowrap', minHeight: '38px', '--service-color': color });
    button.hidden = true;
    const click = event => { event.stopPropagation(); navigate(id); };
    button.addEventListener('click', click);
    labels.appendChild(button);
    labelRecords.push({ button, id, anchor, group, isArch, visible: false });
  }

  function buildDoor(corridor, group, service, side, depth, sequence) {
    const normalLocal = side === 'L' ? V(1, 0, 0) : side === 'R' ? V(-1, 0, 0) : V(0, 0, 1);
    const center = side === 'L' ? V(-COR_HALF, 0, depth) : side === 'R' ? V(COR_HALF, 0, depth) : V(0, 0, depth);
    const facing = Math.atan2(normalLocal.x, normalLocal.z);
    const frame = new THREE.Group(); frame.position.copy(center); frame.rotation.y = facing; group.add(frame);
    const tint = material(corridor.color, { roughness: 0.4, metalness: 0.12 });
    const doorGlass = material(corridor.color, { transparent: true, opacity: 0.28, metalness: 0.18, roughness: 0.16, side: THREE.DoubleSide, depthWrite: false });
    const width = side === 'end' ? 3 : 2.45;
    box(frame, width + 0.35, 0.24, 0.38, M.white, 0, 3.55, 0, true);
    box(frame, 0.19, 3.65, 0.38, M.white, -width / 2 - 0.08, 1.825, 0, true);
    box(frame, 0.19, 3.65, 0.38, M.white, width / 2 + 0.08, 1.825, 0, true);
    box(frame, width + 0.1, 0.065, 0.06, tint, 0, 3.42, 0.23);
    const hinge = new THREE.Group(); hinge.position.x = -width / 2; frame.add(hinge);
    const leaf = box(hinge, width, 3.35, 0.075, doorGlass, width / 2, 1.7, 0, false);
    for (const x of [0.04, width - 0.04]) box(hinge, 0.07, 3.35, 0.11, M.trim, x, 1.7, 0, true);
    for (const y of [0.065, 1.1, 3.34]) box(hinge, width, 0.075, 0.11, M.trim, width / 2, y, 0, true);
    box(hinge, width - 0.2, 0.61, 0.014, M.pale, width / 2, 1.82, 0.052);
    box(hinge, 0.055, 0.66, 0.12, M.dark, width - 0.25, 1.48, 0.14, true);
    const sign = textBoard(frame, service.name, `${service.room || `${corridor.number}${String(sequence + 1).padStart(2, '0')}`}  校园服务空间`, corridor.color, width + 0.12, 0.68, 0, 4.05, 0.21);
    sign.userData.serviceId = service.id; leaf.userData.serviceId = service.id;
    pickables.push(sign, leaf);
    glowBox(frame, width - 0.24, 0.045, 0.07, corridor.color, 0, 3.48, 0.24, false, 0.5);
    glowBox(frame, 0.08, 1.1, 0.07, corridor.color, width / 2 + 0.28, 1.98, 0.24, false, 0.4);
    // Each doorway opens into a deeper themed room. The screen and service desk
    // are pickable hotspots; entering the room itself never opens the workspace.
    const profile = getRoomProfile(service.id);
    // 每个房间拥有独立主题色（屏幕、服务台、灯光、地色），门框与走廊导视仍沿用所属走廊色。
    const accent = profile.accent || corridor.color;
    const roomCenterZ = -ROOM_DEPTH / 2;
    const roomBackZ = -ROOM_DEPTH + 0.08;
    const roomWidth = width + 2.4;
    box(frame, roomWidth, 0.16, ROOM_DEPTH, M.pale, 0, -0.085, roomCenterZ);
    box(frame, roomWidth - 0.28, 0.025, ROOM_DEPTH - 0.28, material(profile.floorTone || corridor.soft, { roughness: 0.5, metalness: 0.02 }), 0, 0.018, roomCenterZ);
    glowBox(frame, roomWidth - 0.7, 0.025, 1.28, accent, 0, 0.041, -1.08, false, 0.28);
    box(frame, roomWidth, ROOM_HEIGHT, 0.16, M.wall, 0, ROOM_HEIGHT / 2, roomBackZ, false, true);
    for (const sideX of [-1, 1]) box(frame, 0.16, ROOM_HEIGHT, ROOM_DEPTH, M.wall, sideX * (width / 2 + 1.15), ROOM_HEIGHT / 2, roomCenterZ, false, true);
    box(frame, roomWidth, 0.18, ROOM_DEPTH, M.white, 0, ROOM_HEIGHT, roomCenterZ);
    box(frame, roomWidth - 0.5, 0.06, 0.05, tint, 0, 0.12, roomBackZ + 0.11);
    glowBox(frame, roomWidth - 0.72, 0.06, 0.08, accent, 0, 2.72, roomBackZ + 0.12, false, 0.36);
    roomLight(frame, accent, -3.75);
    roomScreen(frame, service, accent, -6.25);
    const propZ = [-2.6, -4.6, -5.85];
    profile.facilities.slice(0, 3).forEach((label, index) => {
      // 货架贴侧墙内侧布置（外缘距墙面留 0.1m 间隙），避免穿透墙体或门框。
      const x = index === 1 ? 0 : (index === 0 ? -(width / 2 + 0.28) : width / 2 + 0.28);
      roomProp(frame, label, accent, x, propZ[index], index === 1 ? 'desk' : 'shelf', service.id);
    });
    textBoard(frame, profile.type, service.desc || profile.prompt, accent, 3.9, 0.82, 0, 3.62, roomBackZ + 0.12);
    const p = toWorld(center, corridor);
    const normal = normalLocal.clone().applyAxisAngle(UP, corridor.rot);
    const door = { id: service.id, group: corridor.id, hinge, width, center: p, normal, view: p.clone().addScaledVector(normal, 3.2).setY(EYE), inside: p.clone().addScaledVector(normal, -3.0).setY(EYE), yaw: Math.atan2(normal.x, normal.z), target: 0, open: 0 };
    doors.set(service.id, door);
    makeLabel(service.id, service.name, service.room || '', p.clone().addScaledVector(normal, 0.46).setY(4.65), corridor.id);
  }

  for (const corridor of CORRIDORS) {
    const group = new THREE.Group(); group.rotation.y = corridor.rot; scene.add(group);
    const tone = material(corridor.color);
    floor(group, COR_HALF * 2, 24, 0, -23);
    glowBox(group, 0.08, 0.018, 23.3, corridor.color, -3.45, 0.028, -22.9, false, 0.32);
    glowBox(group, 0.08, 0.018, 23.3, corridor.color, 3.45, 0.028, -22.9, false, 0.32);
    for (const x of [-3.45, 3.45]) box(group, 0.055, 0.012, 23.4, tone, x, 0.012, -22.9);
    for (const z of [-13.3, -19.2, -25.2, -31.2]) { ceilingStrip(group, corridor.color, z); wayfindingArrow(group, corridor.color, z + 0.9); }
    // White portal frames establish the three destinations before any room is selected.
    for (const x of [-COR_HALF, COR_HALF]) box(group, 0.45, 6.35, 0.65, M.white, x, 3.175, -10.8, true);
    box(group, 8.7, 0.65, 0.68, M.white, 0, 6.02, -10.8, true);
    box(group, 7.75, 0.085, 0.07, tone, 0, 5.65, -10.4);
    textBoard(group, corridor.name, corridor.caption, corridor.color, 5.0, 1.16, 0, 5.07, -10.39);
    glowBox(group, 4.8, 0.045, 0.08, corridor.color, 0, 0.23, -14.25, false, 0.34);
    for (const z of [-14, -22, -30, -35]) {
      box(group, 8.3, 0.18, 0.24, M.white, 0, 5.2, z, true);
      box(group, 6.8, 0.035, 0.08, M.light, 0, 5.08, z);
    }
    box(group, 8.3, 0.035, 23.4, M.glass, 0, 5.28, -23.0);
    const members = services.filter(item => item.group === corridor.id);
    const placements = members.map((service, index) => ({ service, side: corridor.id === 'ai' && index === 2 ? 'end' : index % 2 ? 'R' : 'L', z: corridor.id === 'ai' && index === 2 ? END : index < 2 ? -17 : -27 }));
    for (const side of ['L', 'R']) {
      const sx = side === 'L' ? -COR_HALF : COR_HALF;
      const cuts = placements.filter(item => item.side === side).map(item => item.z).sort((a, b) => b - a);
      let front = -11.2;
      for (const cut of cuts) {
        const back = cut + 1.48;
        if (front > back) box(group, 0.22, 4.8, front - back, M.wall, sx, 2.4, (front + back) / 2, true, true);
        box(group, 0.22, 1.2, 2.96, M.wall, sx, 4.2, cut, true, true);
        front = cut - 1.48;
      }
      if (front > END) box(group, 0.22, 4.8, front - END, M.wall, sx, 2.4, (front + END) / 2, true, true);
      box(group, 0.07, 0.11, 23.5, tone, sx + (side === 'L' ? 0.14 : -0.14), 0.19, -23.2);
    }
    if (corridor.id !== 'ai') {
      box(group, 8.3, 4.8, 0.2, M.wall, 0, 2.4, END, true, true);
      glowBox(group, 5.3, 0.08, 0.06, corridor.color, 0, 2.05, END + 0.18, false, 0.36);
      planter(group, -2.8, -33.5, 0.9);
      planter(group, 2.8, -33.5, 0.9);
    } else {
      for (const side of [-1, 1]) box(group, 2.65, 4.8, 0.22, M.wall, side * 2.85, 2.4, END, true, true);
      box(group, 3.1, 1.1, 0.22, M.wall, 0, 4.25, END, true, true);
      glowBox(group, 3.4, 0.08, 0.06, corridor.color, 0, 2.05, END + 0.16, false, 0.36);
    }
    placements.forEach((item, index) => buildDoor(corridor, group, item.service, item.side, item.z, index));
    const groupMeta = groupArray.find(item => item.id === corridor.id);
    if (members.length) makeLabel(members[0].id, groupMeta?.name || corridor.name, `${members.length} 项服务`, toWorld(V(0, 5.84, -10.1), corridor), corridor.id, true);
  }
  makeLabel('portal', '综合门户', '梧桐树下', V(0, 2.0, 2.3), 'hub');
  scene.updateMatrixWorld(true);

  // A single timeline is cancellable at every phase. No delayed entry callbacks survive it.
  const position = HOME.clone();
  let yaw = HOME_YAW, pitch = 0.025;
  let disposed = false, pausedByApp = false, hidden = document.hidden;
  let comfort = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  let serial = 0, plan = null, insideId = null, lastEnteredId = null, phase = 'idle', targetId = null;
  let raf = 0, elapsedTime = 0, lastTime = performance.now(), lastPositionTime = -1, lastLabelTime = -1, lastProgress = -1;
  let width = 1, height = 1, pointerDown = null, hoveredId = null, hoveredObject = null, pickedObject = null;
  const keys = new Set();
  const raycaster = new THREE.Raycaster();
  const pointer = new THREE.Vector2();
  const projected = V();
  const direction = V();
  const progressEvent = (id, nextPhase, progress = 0) => {
    phase = nextPhase;
    onNavigate?.({ phase: nextPhase, id, progress });
  };
  function setLandscapeVisible(value) { landscapeObjects.forEach(object => { object.visible = value; }); }
  function zoneOf(point) {
    for (const corridor of CORRIDORS) {
      const local = localOf(point, corridor);
      if (local.z < -10.35 && Math.abs(local.x) < 8) return corridor.id;
    }
    return 'hub';
  }
  function applyCamera() {
    camera.position.copy(position);
    camera.rotation.set(pitch, yaw, 0, 'YXZ');
    camera.updateMatrixWorld();
  }
  function reportPosition(force = false) {
    if (force || elapsedTime - lastPositionTime > 0.12) {
      lastPositionTime = elapsedTime;
      onPosition?.({ x: position.x, z: position.z, yaw, zone: insideId && insideId !== 'portal' ? doors.get(insideId)?.group || zoneOf(position) : zoneOf(position) });
    }
  }
  function closestRoom(point) {
    for (const door of doors.values()) {
      const diff = point.clone().sub(door.center);
      const along = diff.dot(door.normal);
      const across = Math.abs(diff.x * door.normal.z - diff.z * door.normal.x);
      if (along < 0.4 && along > -ROOM_DEPTH - 0.35 && across < door.width / 2 + 1.05) return door;
    }
    return null;
  }
  function closeDoors(except = null) { for (const door of doors.values()) door.target = door.id === except ? 1 : 0; }
  function cancel() {
    serial += 1;
    plan = null;
    keys.clear();
    targetId = null;
    lastProgress = -1;
    const currentRoom = closestRoom(position);
    closeDoors(currentRoom?.id || null);
  }
  function lineDistance(a, b) {
    const dx = b.x - a.x, dz = b.z - a.z;
    const t = THREE.MathUtils.clamp(-(a.x * dx + a.z * dz) / (dx * dx + dz * dz || 1), 0, 1);
    return Math.hypot(a.x + dx * t, a.z + dz * t);
  }
  function addHallPath(points, a, b) {
    if (lineDistance(a, b) < 3.5) {
      const aa = Math.atan2(a.z, a.x), ab = Math.atan2(b.z, b.x);
      const delta = Math.atan2(Math.sin(ab - aa), Math.cos(ab - aa));
      const steps = Math.max(2, Math.ceil(Math.abs(delta) / (Math.PI / 7)));
      for (let index = 0; index <= steps; index++) {
        const angle = aa + delta * index / steps;
        points.push(V(Math.cos(angle) * 4.7, EYE, Math.sin(angle) * 4.7));
      }
    }
    points.push(b.clone());
  }
  function route(from, to) {
    const points = [from.clone()];
    const za = zoneOf(from), zb = zoneOf(to);
    if (za === zb && za !== 'hub') { points.push(to.clone()); return points; }
    let a = from.clone();
    if (za !== 'hub') {
      a = toWorld(V(0, EYE, -9.45), CORRIDORS.find(item => item.id === za));
      points.push(a);
    }
    const b = zb !== 'hub' ? toWorld(V(0, EYE, -9.45), CORRIDORS.find(item => item.id === zb)) : to.clone();
    addHallPath(points, a, b);
    if (zb !== 'hub') points.push(to.clone());
    return points;
  }
  function movement(points, endYaw, kind = 'walking', duration) {
    const lengths = [];
    let length = 0;
    for (let index = 1; index < points.length; index++) { length += points[index - 1].distanceTo(points[index]); lengths.push(length); }
    return { kind, points, lengths, length, endYaw, duration: duration ?? Math.max(0.65, Math.min(5.6, length / 7.8)) };
  }
  function pointOnMove(stage, value) {
    if (!stage.length) return stage.points.at(-1).clone();
    const wanted = value * stage.length;
    let index = stage.lengths.findIndex(length => length >= wanted);
    if (index < 0) index = stage.lengths.length - 1;
    const previous = index ? stage.lengths[index - 1] : 0;
    const denominator = stage.lengths[index] - previous || 1;
    return stage.points[index].clone().lerp(stage.points[index + 1], (wanted - previous) / denominator);
  }
  function startPlan(action, id, stages, instant) {
    const token = serial;
    targetId = id;
    insideId = null;
    plan = { token, action, id, stages, index: 0, time: 0, elapsed: 0, total: stages.reduce((sum, stage) => sum + stage.duration, 0) };
    if (instant || comfort) {
      for (const stage of stages) {
        if (stage.points) { position.copy(stage.points.at(-1)); if (Number.isFinite(stage.endYaw)) yaw = stage.endYaw; }
        if (stage.kind === 'opening' && doors.has(id)) doors.get(id).target = 1;
      }
      pitch = 0;
      finishPlan(token);
      applyCamera(); reportPosition(true); updateLabels(true); renderOnce();
      return;
    }
    progressEvent(id, stages[0]?.kind === 'opening' ? 'opening' : 'walking', 0);
  }
  function finishPlan(token) {
    if (!plan || token !== serial || token !== plan.token) return;
    const { action, id } = plan;
    plan = null;
    targetId = null;
    if (action === 'enter') {
      insideId = id;
      lastEnteredId = id;
      setLandscapeVisible(false);
      closeDoors(id);
      progressEvent(id, 'inside', 1);
      onEnter?.(id);
    } else {
      insideId = null;
      lastEnteredId = null;
      setLandscapeVisible(true);
      closeDoors();
      progressEvent(null, 'idle', 1);
    }
    reportPosition(true);
  }
  function navigate(id, { instant = false } = {}) {
    if (disposed || (!doors.has(id) && id !== 'portal') || !serviceMap.has(id)) return false;
    if (insideId === id && !plan) return true;
    // Only an explicit insideId means the camera is inside a room. Proximity to a
    // doorway is not enough to add an exit stage, otherwise entering a door can
    // briefly move backward before heading toward the selected destination.
    const room = insideId ? doors.get(insideId) : null;
    cancel();
    const stages = [];
    let start = position.clone();
    if (room) {
      room.target = 1;
      stages.push(movement([start, room.view.clone()], room.yaw, 'exiting', 0.62));
      start = room.view.clone();
    }
    const door = doors.get(id);
    const nearDoor = !!door && position.distanceTo(door.view) < 1.65;
    const destination = door ? (nearDoor ? start.clone() : door.view) : V(0, EYE, 6.25);
    const endYaw = door ? door.yaw : 0;
    const approach = route(start, destination);
    if (approach.length > 1 && approach[0].distanceTo(approach.at(-1)) > 0.08) stages.push(movement(approach, endYaw));
    if (door) {
      stages.push({ kind: 'opening', duration: 0.45 });
      stages.push(movement([(nearDoor ? start : door.view).clone(), door.inside.clone()], door.yaw, 'entering', 0.85));
    }
    startPlan('enter', id, stages, instant);
    return true;
  }
  function exit(roomId = null, { instant = false } = {}) {
    if (disposed) return;
    const requestedRoom = roomId && doors.has(roomId) ? doors.get(roomId) : null;
    const room = (insideId && doors.get(insideId)) || requestedRoom || (lastEnteredId && doors.get(lastEnteredId)) || closestRoom(position);
    cancel(); insideId = null;
    if (room) {
      lastEnteredId = room.id;
      room.target = 1;
      startPlan('exit', room.id, [movement([position.clone(), room.view.clone()], room.yaw, 'exiting', 0.75)], instant);
    } else { closeDoors(); progressEvent(null, 'idle', 0); reportPosition(true); }
  }
  function goHall() {
    if (disposed) return;
    const room = (insideId && doors.get(insideId)) || (lastEnteredId && doors.get(lastEnteredId)) || null;
    cancel(); insideId = null;
    let start = position.clone();
    const stages = [];
    if (room) { room.target = 1; stages.push(movement([start, room.view.clone()], room.yaw, 'exiting', 0.6)); start = room.view.clone(); }
    stages.push(movement(route(start, HOME), HOME_YAW));
    startPlan('hall', null, stages, false);
  }
  function lerpAngle(from, to, t) { return from + Math.atan2(Math.sin(to - from), Math.cos(to - from)) * t; }
  function updatePlan(dt) {
    if (!plan) return;
    const active = plan;
    if (active.token !== serial) { plan = null; return; }
    const stage = active.stages[active.index];
    if (!stage) { finishPlan(active.token); return; }
    active.time += dt;
    active.elapsed += dt;
    const progress = Math.min(1, active.time / stage.duration);
    const nextPhase = stage.kind === 'opening' || stage.kind === 'entering' ? 'opening' : 'walking';
    if (phase !== nextPhase) progressEvent(active.id, nextPhase, Math.min(1, active.elapsed / active.total));
    if (stage.kind === 'opening') {
      const door = doors.get(active.id);
      if (door) door.target = 1;
    } else {
      const smooth = progress * progress * (3 - 2 * progress);
      const point = pointOnMove(stage, smooth);
      const ahead = pointOnMove(stage, Math.min(1, smooth + 0.025));
      let targetYaw = Math.atan2(-(ahead.x - point.x), -(ahead.z - point.z));
      if (progress > 0.68 || stage.kind === 'entering' || stage.kind === 'exiting') targetYaw = stage.endYaw;
      yaw = lerpAngle(yaw, targetYaw, Math.min(1, dt * 7));
      pitch *= Math.max(0, 1 - dt * 5);
      position.copy(point);
    }
    if (active.elapsed - lastProgress > 0.12) {
      lastProgress = active.elapsed;
      onNavigate?.({ phase: nextPhase, id: active.id, progress: Math.min(1, active.elapsed / active.total) });
    }
    if (progress >= 1 && plan === active) {
      if (stage.points) { position.copy(stage.points.at(-1)); if (Number.isFinite(stage.endYaw)) yaw = stage.endYaw; }
      if (stage.kind === 'exiting') closeDoors();
      active.index += 1;
      active.time = 0;
      if (active.index >= active.stages.length) finishPlan(active.token);
    }
  }
  function walkable(point) {
    if (Math.abs(point.x) < 10.5 && Math.abs(point.z) < 10.5 && Math.hypot(point.x, point.z) > 3.12) return true;
    for (const corridor of CORRIDORS) {
      const local = localOf(point, corridor);
      if (Math.abs(local.x) < COR_HALF - 0.38 && local.z < -9.9 && local.z > END + 0.45) return true;
    }
    const room = closestRoom(point);
    return !!room && room.target > 0.5 && roomWalkable(point, room);
  }
  function roomWalkable(point, room) {
    const delta = point.clone().sub(room.center);
    const along = -delta.dot(room.normal);
    const across = Math.abs(delta.x * room.normal.z - delta.z * room.normal.x);
    return along > 0.55 && along < ROOM_DEPTH - 0.62 && across < room.width / 2 + 0.92;
  }
  function moveManually(dt) {
    if (!keys.size) return;
    const forward = Number(keys.has('w') || keys.has('arrowup')) - Number(keys.has('s') || keys.has('arrowdown'));
    const sideways = Number(keys.has('d') || keys.has('arrowright')) - Number(keys.has('a') || keys.has('arrowleft'));
    if (!forward && !sideways) return;
    const distance = 4.7 * dt / Math.hypot(forward, sideways);
    const dx = (-Math.sin(yaw) * forward + Math.cos(yaw) * sideways) * distance;
    const dz = (-Math.cos(yaw) * forward - Math.sin(yaw) * sideways) * distance;
    const room = insideId ? doors.get(insideId) : null;
    const canMove = point => room ? roomWalkable(point, room) : walkable(point);
    if (canMove(V(position.x + dx, EYE, position.z + dz))) position.set(position.x + dx, EYE, position.z + dz);
    else if (canMove(V(position.x + dx, EYE, position.z))) position.x += dx;
    else if (canMove(V(position.x, EYE, position.z + dz))) position.z += dz;
  }
  function nearestDoor() {
    let nearest = null, best = 3.8;
    for (const door of doors.values()) {
      const distance = position.distanceTo(door.center.clone().setY(EYE));
      if (distance < best) { best = distance; nearest = door; }
    }
    return nearest;
  }
  function eventOn(element, event, handler, options) { element.addEventListener(event, handler, options); listeners.push(() => element.removeEventListener(event, handler, options)); }
  function isEditing(target) { return target instanceof Element && !!target.closest('input,textarea,select,[contenteditable="true"],[role="dialog"]'); }
  function active() { return !disposed && !pausedByApp && !hidden; }
  function updateHotspotHover(next) {
    if (hoveredObject === next) return;
    if (hoveredObject?.userData.hoverBaseScale) hoveredObject.scale.copy(hoveredObject.userData.hoverBaseScale);
    hoveredObject = next?.userData.hotspot ? next : null;
    if (hoveredObject) {
      hoveredObject.userData.hoverBaseScale ||= hoveredObject.scale.clone();
      hoveredObject.scale.copy(hoveredObject.userData.hoverBaseScale).multiplyScalar(1.025);
    }
  }
  eventOn(window, 'keydown', event => {
    if (!active() || isEditing(event.target) || event.ctrlKey || event.metaKey || event.altKey) return;
    const key = event.key.toLowerCase();
    if (key === 'e') { const door = nearestDoor(); if (door && !insideId) { event.preventDefault(); navigate(door.id); } return; }
    if (!['w', 'a', 's', 'd', 'arrowup', 'arrowdown', 'arrowleft', 'arrowright'].includes(key)) return;
    event.preventDefault();
    if (plan && ['opening', 'entering', 'exiting'].includes(plan.stages[plan.index]?.kind)) return;
    if (plan) { cancel(); progressEvent(null, 'idle', 0); }
    keys.add(key);
  });
  eventOn(window, 'keyup', event => keys.delete(event.key.toLowerCase()));
  eventOn(window, 'blur', () => { keys.clear(); pointerDown = null; });
  eventOn(document, 'visibilitychange', () => { hidden = document.hidden; keys.clear(); lastTime = performance.now(); });
  function pickAt(event) {
    const rect = canvas.getBoundingClientRect();
    pointer.set((event.clientX - rect.left) / rect.width * 2 - 1, -(event.clientY - rect.top) / rect.height * 2 + 1);
    raycaster.setFromCamera(pointer, camera);
    const hit = raycaster.intersectObjects(pickables, false)[0];
    if (!hit) { pickedObject = null; return null; }
    const obstruction = raycaster.intersectObjects(walls, false)[0];
    if (obstruction && obstruction.distance <= hit.distance - 0.15) { pickedObject = null; return null; }
    pickedObject = hit.object;
    return hit.object.userData.serviceId || null;
  }
  eventOn(canvas, 'pointerdown', event => {
    if (!active() || event.button > 0) return;
    pointerDown = { id: event.pointerId, x: event.clientX, y: event.clientY, travel: 0 };
    canvas.focus({ preventScroll: true });
    canvas.setPointerCapture?.(event.pointerId);
  });
  eventOn(canvas, 'pointermove', event => {
    if (!active()) return;
    if (!pointerDown) { hoveredId = pickAt(event); updateHotspotHover(pickedObject); canvas.style.cursor = hoveredId ? 'pointer' : 'grab'; return; }
    const dx = event.clientX - pointerDown.x, dy = event.clientY - pointerDown.y;
    pointerDown.travel += Math.abs(dx) + Math.abs(dy);
    pointerDown.x = event.clientX; pointerDown.y = event.clientY;
    if (!plan) { yaw -= dx * 0.0036; pitch = THREE.MathUtils.clamp(pitch - dy * 0.0028, -0.56, 0.65); }
    canvas.style.cursor = 'grabbing';
  });
  eventOn(canvas, 'pointerup', event => {
    if (pointerDown?.id !== event.pointerId) return;
    const click = pointerDown.travel < 7;
    pointerDown = null;
    if (canvas.hasPointerCapture?.(event.pointerId)) canvas.releasePointerCapture(event.pointerId);
    canvas.style.cursor = 'grab';
    if (click && active()) {
      const id = pickAt(event);
      if (id && insideId) { if (id === insideId) onRoomInteract?.(id); }
      else if (id) navigate(id);
    }
  });
  eventOn(canvas, 'pointercancel', () => { pointerDown = null; });
  eventOn(canvas, 'pointerleave', () => { hoveredId = null; pickedObject = null; updateHotspotHover(null); });
  eventOn(canvas, 'webglcontextlost', event => {
    event.preventDefault(); pausedByApp = true; cancel();
    onError?.(new Error('三维画面暂时不可用，已保留全部校园服务。'));
  });

  function updateLabels(force = false) {
    const checkOcclusion = force || elapsedTime - lastLabelTime > 0.18;
    if (checkOcclusion) lastLabelTime = elapsedTime;
    const zone = zoneOf(position);
    for (const record of labelRecords) {
      if (insideId || disposed || pausedByApp) { record.button.hidden = true; continue; }
      projected.copy(record.anchor).project(camera);
      const distance = record.anchor.distanceTo(position);
      let visible = projected.z > -1 && projected.z < 1 && Math.abs(projected.x) < 0.97 && projected.y > -0.92 && projected.y < 0.92;
      visible &&= record.isArch ? zone === 'hub' && distance < 30 : record.group === 'hub' ? zone === 'hub' && distance < 18 : record.group === zone && distance < 21;
      if (visible && !record.isArch && doors.has(record.id)) visible = position.clone().sub(doors.get(record.id).center).dot(doors.get(record.id).normal) > 0.12;
      if (visible && checkOcclusion) {
        direction.copy(record.anchor).sub(position).normalize();
        raycaster.set(position, direction); raycaster.far = distance - 0.22;
        record.visible = !raycaster.intersectObjects(walls, false).length;
        raycaster.far = Infinity;
      }
      visible &&= record.visible || (record.isArch && distance < 13);
      record.button.hidden = !visible;
      if (visible) {
        record.button.style.left = `${(projected.x * 0.5 + 0.5) * width}px`;
        record.button.style.top = `${(-projected.y * 0.5 + 0.5) * height}px`;
        record.button.classList.toggle('is-active', targetId === record.id || hoveredId === record.id);
      }
    }
  }
  function renderOnce() {
    if (disposed) return;
    try { renderer.render(scene, camera); } catch (error) { pausedByApp = true; onError?.(error); }
  }
  function tick(now) {
    if (disposed) return;
    raf = requestAnimationFrame(tick);
    const dt = Math.min((now - lastTime) / 1000, 0.05);
    lastTime = now;
    if (!active()) return;
    elapsedTime += dt;
    if (plan) updatePlan(dt); else moveManually(dt);
    for (const door of doors.values()) {
      door.open += (door.target - door.open) * Math.min(1, dt * (comfort ? 100 : 7.5));
      door.hinge.rotation.y = -door.open * Math.PI * 0.52;
    }
    if (!comfort) {
      for (const leaf of leaves) leaf.mesh.rotation.z = leaf.rotation + Math.sin(elapsedTime * 0.45 + leaf.offset) * 0.008;
      for (const source of glowSources) source.material.emissiveIntensity = source.base + Math.sin(elapsedTime * 1.7 + source.phase) * 0.11;
    }
    applyCamera(); updateLabels(); reportPosition(); renderOnce();
  }
  function resize() {
    const rect = container.getBoundingClientRect();
    width = Math.max(1, rect.width); height = Math.max(1, rect.height);
    renderer.setSize(width, height, false);
    camera.aspect = width / height;
    camera.fov = width / height < 0.8 ? 77 : 66;
    camera.updateProjectionMatrix();
    applyCamera(); updateLabels(true); renderOnce();
  }
  const observer = new ResizeObserver(resize);
  observer.observe(container);
  resize(); applyCamera(); reportPosition(true);
  raf = requestAnimationFrame(tick);

  function restoreState(state = {}) {
    if (disposed) return false;
    const roomId = typeof state.insideId === 'string' && doors.has(state.insideId) ? state.insideId : null;
    const rawPosition = Array.isArray(state.position) ? state.position : (Array.isArray(state.pos) ? state.pos : null);
    const hasPosition = rawPosition && rawPosition.length >= 3 && rawPosition.slice(0, 3).every(Number.isFinite);
    cancel();
    if (hasPosition) position.set(rawPosition[0], rawPosition[1], rawPosition[2]);
    else position.copy(HOME);
    yaw = Number.isFinite(state.yaw) ? state.yaw : HOME_YAW;
    pitch = Number.isFinite(state.pitch) ? state.pitch : 0.025;
    insideId = roomId;
    lastEnteredId = roomId;
    if (roomId) {
      setLandscapeVisible(false);
      closeDoors(roomId);
      doors.get(roomId).target = 1;
    } else {
      setLandscapeVisible(true);
      closeDoors();
    }
    applyCamera(); reportPosition(true); updateLabels(true); renderOnce();
    return true;
  }

  return {
    navigate, exit, goHall, restoreState,
    cancelNavigation() { cancel(); progressEvent(null, 'idle', 0); },
    setQuality(quality) {
      // 流畅：像素比 1、关闭实时阴影；均衡：像素比 1.5、1024 阴影图；高清：像素比 2、2048 阴影图。
      // 色彩统一由 ACES Filmic 色调映射 + sRGB 输出处理（轻量内建后处理），不引入重型 EffectComposer 以保证帧率。
      const ratio = quality === 'low' ? 1 : quality === 'high' ? 2 : 1.5;
      renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, ratio));
      renderer.shadowMap.enabled = quality !== 'low';
      const mapSize = quality === 'high' ? 2048 : 1024;
      if (sun.shadow.mapSize.width !== mapSize || sun.shadow.mapSize.height !== mapSize) {
        sun.shadow.mapSize.set(mapSize, mapSize);
        sun.shadow.map?.dispose();
        sun.shadow.map = null;
      }
      renderer.shadowMap.needsUpdate = true;
      resize();
      renderOnce();
    },
    setPaused(value) {
      pausedByApp = !!value; keys.clear(); pointerDown = null; lastTime = performance.now();
      if (pausedByApp) for (const record of labelRecords) record.button.hidden = true;
      else { applyCamera(); updateLabels(true); renderOnce(); }
    },
    setTheme(theme) {
      const dark = theme === 'dark';
      scene.background.set(dark ? 0x1e344d : 0xe4f1fc);
      scene.fog.color.copy(scene.background);
      hemi.intensity = dark ? 1.35 : 2.3;
      sun.intensity = dark ? 1.3 : 2.7;
      renderer.toneMappingExposure = dark ? 0.85 : 1.1;
      M.wall.color.set(dark ? 0x8098b0 : 0xecf3fa);
      M.floor.color.set(dark ? 0x68839d : 0xd9e8f3);
      M.white.color.set(dark ? 0xb5c8d9 : 0xf8fbff);
      renderOnce();
    },
    setComfort(value) {
      comfort = !!value;
      for (const leaf of leaves) leaf.mesh.rotation.z = leaf.rotation;
      for (const source of glowSources) source.material.emissiveIntensity = source.base;
      if (comfort && plan) {
        const last = [...plan.stages].reverse().find(stage => stage.points);
        if (last) { position.copy(last.points.at(-1)); yaw = last.endYaw ?? yaw; pitch = 0; }
        finishPlan(plan.token);
      }
      applyCamera(); updateLabels(true); renderOnce();
    },
    getDebug() {
    return { phase, insideId, lastEnteredId, targetId, pos: position.toArray().map(n => +n.toFixed(3)), yaw: +yaw.toFixed(3), pitch: +pitch.toFixed(3), zone: zoneOf(position), paused: pausedByApp || hidden, comfort, serial, stage: plan?.stages[plan.index]?.kind || null, doors: [...doors.values()].map(door => ({ id: door.id, group: door.group, center: door.center.toArray(), view: door.view.toArray(), inside: door.inside.toArray(), open: +door.open.toFixed(2) })), labels: labelRecords.filter(record => !record.button.hidden).map(record => record.id) };
    },
    destroy() {
      if (disposed) return;
      disposed = true; cancel(); cancelAnimationFrame(raf); observer.disconnect();
      listeners.forEach(remove => remove());
      labelRecords.forEach(record => record.button.remove());
      for (const item of disposables) item.dispose?.();
      sun.shadow.map?.dispose();
      renderer.dispose();
      // 立即释放 WebGL 上下文，确保多次进出 3D 页面不会累积 GPU 上下文与内存。
      renderer.forceContextLoss?.();
      canvas.remove();
    }
  };
}
