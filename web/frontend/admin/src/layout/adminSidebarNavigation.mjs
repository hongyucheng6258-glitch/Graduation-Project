export function matchesNavigationPath(to, path) {
  if (to === '/dashboard' || to === '/system') return path === to
  return path === to || path.startsWith(`${to}/`)
}

export function findNavigationGroup(groups, path) {
  return groups.find((group) =>
    group.items.some((item) => matchesNavigationPath(item.to, path))
  )?.label ?? null
}

export function toggleExpandedGroup(currentGroup, targetGroup) {
  return currentGroup === targetGroup ? null : targetGroup
}

export function syncExpandedGroupForRoute(expandedGroup, previousRouteGroup, nextRouteGroup) {
  if (!nextRouteGroup || nextRouteGroup === previousRouteGroup) return expandedGroup
  return nextRouteGroup
}
