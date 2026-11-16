-- Fill the missing default without replacing an administrator's existing template.
INSERT INTO `prompt_template` (`scene`, `name`, `content`)
SELECT 'practice', '同类练习模板',
    '请根据原错题生成一道同类型、同难度的新练习题。只输出 JSON 对象，不要输出 Markdown 或其他文字。\n格式：{"question":"新题题目","options":[],"answer":"正确答案","analysis":"详细解析"}\n选择题的 options 填写带字母的选项，answer 填写正确选项字母；其他题型的 options 为空数组。\n学科：{subject}\n原题：{question_text}\n原答案：{correct_answer}\n原解析：{analysis}'
WHERE NOT EXISTS (SELECT 1 FROM `prompt_template` WHERE `scene` = 'practice');
