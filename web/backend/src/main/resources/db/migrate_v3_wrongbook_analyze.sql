-- ============================================================
-- 错题本 v3 增量迁移（在 v2 基础上执行）
-- 新增 analyze_status：AI 智能整理状态（0未整理 1整理失败 2已整理）
-- 已执行过 v2 迁移的库执行本脚本即可；v1 旧库需先执行 migrate_v2_wrongbook.sql
-- ============================================================

ALTER TABLE `wrong_question`
  ADD COLUMN `analyze_status` TINYINT NOT NULL DEFAULT 0 COMMENT 'AI整理状态 0未整理 1整理失败 2已整理' AFTER `note`;

-- 新场景提示词模板（幂等：按 scene 存在则跳过）
INSERT INTO `prompt_template` (`scene`, `name`, `content`)
SELECT * FROM (SELECT 'wrong_analyze' AS scene, '错题智能整理模板' AS name,
  '你是一名资深学科老师。请分析以下错题，只输出一个 JSON 对象（不要输出任何其他文字或代码块标记），字段如下：\n{\n  "questionType": "题型，如：选择/填空/简答/计算/编程",\n  "subject": "推测的学科，如：Java/高等数学/数据结构",\n  "chapter": "所属章节",\n  "difficulty": "难度：易/中/难",\n  "knowledgePoints": "知识点数组，如：[\\"多线程\\",\\"锁\\"]",\n  "errorReason": "错因：概念不清/公式记错/审题错误/计算错误/粗心大意/不会解题/知识点混淆/其他",\n  "summary": "不超过50字的本题知识点摘要"\n}\n\n学科（已知）：{subject}\n题目：{question_text}\n我的答案：{my_answer}\n正确答案：{correct_answer}\n解析：{analysis}' AS content) AS tmp
WHERE NOT EXISTS (SELECT 1 FROM `prompt_template` WHERE `scene` = 'wrong_analyze');

INSERT INTO `prompt_template` (`scene`, `name`, `content`)
SELECT * FROM (SELECT 'wrong_explain' AS scene, '错题讲解模板' AS name,
  '你是一名耐心的学科辅导老师。请针对学生做错的这道题，输出 Markdown 格式的讲解：\n1. 【错误分析】指出学生的答案错在哪里、为什么错\n2. 【知识点讲解】讲解本题涉及的核心知识点，分步骤说明\n3. 【正确思路】给出正确的解题思路与答案\n4. 【易错提醒】一句话总结以后再遇到这类题要注意什么\n\n学科：{subject}\n题目：{question_text}\n我的答案：{my_answer}\n正确答案：{correct_answer}\n解析：{analysis}\n错因：{error_reason}' AS content) AS tmp
WHERE NOT EXISTS (SELECT 1 FROM `prompt_template` WHERE `scene` = 'wrong_explain');

INSERT INTO `prompt_template` (`scene`, `name`, `content`)
SELECT * FROM (SELECT 'review_plan' AS scene, '复习计划模板' AS name,
  '你是一名学习规划师。请根据学生错题本中的待复习题目，生成一份今天的复习计划，输出 Markdown：\n1. 【今日复习清单】按优先级列出待复习题目及理由\n2. 【推荐复习顺序】说明先复习什么、后复习什么\n3. 【复习方法建议】针对不同错因给出对应复习方法\n4. 【自测问题】2-3 个检验掌握程度的问题\n\n学科筛选：{subject}\n待复习题目列表：\n{question_list}' AS content) AS tmp
WHERE NOT EXISTS (SELECT 1 FROM `prompt_template` WHERE `scene` = 'review_plan');
