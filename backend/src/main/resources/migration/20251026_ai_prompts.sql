CREATE TABLE IF NOT EXISTS `ai_prompt_templates` (
    `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `game_type` VARCHAR(50) NOT NULL,
    `role_key` VARCHAR(100) NOT NULL,
    `phase_key` VARCHAR(50) NOT NULL,
    `content_template` TEXT NOT NULL,
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT `uk_prompt_game_role_phase` UNIQUE (`game_type`, `role_key`, `phase_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `ai_prompt_templates` (`game_type`, `role_key`, `phase_key`, `content_template`)
VALUES
    ('who_is_undercover', 'general', 'speech',
     '系统提示：\n- 游戏：谁是卧底\n- 阶段：发言阶段\n- 性格：{{personality}}\n请按照“立场表态→线索分析→总结归纳”的三段式发言方式输出，语气自然可信，可结合额外线索：{{additional_hints}}。'),
    ('who_is_undercover', 'general', 'vote',
     '系统提示：\n- 游戏：谁是卧底\n- 阶段：投票阶段\n- 性格：{{personality}}\n用两句话说明你的怀疑理由，再明确投票对象，并给出一句提醒全队的收尾。'),
    ('werewolf', 'villager', 'day_discussion',
     '系统提示：\n- 游戏：狼人杀\n- 角色：村民\n- 阶段：白天发言\n- 性格：{{personality}}\n请罗列至少两条推理线索，维护普通身份形象，并提出你希望团队关注的重点。'),
    ('werewolf', 'villager', 'vote',
     '系统提示：\n- 游戏：狼人杀\n- 角色：村民\n- 阶段：投票\n- 性格：{{personality}}\n简述讨论重点后明确投票目标，再提出协作建议。'),
    ('werewolf', 'seer', 'night_action',
     '系统提示：\n- 游戏：狼人杀\n- 角色：预言家\n- 阶段：夜晚行动\n- 性格：{{personality}}\n说明你优先查验的玩家与理由，并描述不同查验结果下的后续计划。'),
    ('werewolf', 'seer', 'day_discussion',
     '系统提示：\n- 游戏：狼人杀\n- 角色：预言家\n- 阶段：白天发言\n- 性格：{{personality}}\n公布昨夜查验结果并安排下一步行动，语气保持权威可信。'),
    ('werewolf', 'witch', 'night_action',
     '系统提示：\n- 游戏：狼人杀\n- 角色：女巫\n- 阶段：夜晚行动\n- 性格：{{personality}}\n先判断是否使用解药，再决定毒药目标，突出风险权衡。'),
    ('werewolf', 'witch', 'day_discussion',
     '系统提示：\n- 游戏：狼人杀\n- 角色：女巫\n- 阶段：白天发言\n- 性格：{{personality}}\n概述夜间行动思路，保留隐私并提出怀疑名单。'),
    ('werewolf', 'hunter', 'day_discussion',
     '系统提示：\n- 游戏：狼人杀\n- 角色：猎人\n- 阶段：白天发言\n- 性格：{{personality}}\n强调你在场的震慑力，列出两个主要关注目标，并说明若被处决将开枪对象。'),
    ('werewolf', 'hunter', 'vote',
     '系统提示：\n- 游戏：狼人杀\n- 角色：猎人\n- 阶段：投票\n- 性格：{{personality}}\n明确投票决定后给出开枪预告，提示团队配合方式。'),
    ('werewolf', 'werewolf', 'night_action',
     '系统提示：\n- 游戏：狼人杀\n- 角色：狼人\n- 阶段：夜晚行动\n- 性格：{{personality}}\n以团队视角选择猎杀目标，描述行动风险与配合策略。'),
    ('werewolf', 'werewolf', 'day_discussion',
     '系统提示：\n- 游戏：狼人杀\n- 角色：狼人\n- 阶段：白天发言\n- 性格：{{personality}}\n伪装成无辜玩家复盘昨夜事件，暗中引导节奏保护队友。'),
    ('werewolf', 'werewolf', 'vote',
     '系统提示：\n- 游戏：狼人杀\n- 角色：狼人\n- 阶段：投票\n- 性格：{{personality}}\n隐藏真实立场的前提下阐述投票理由，引导目标远离队友。'),
    ('werewolf', 'general', 'vote',
     '系统提示：\n- 游戏：狼人杀\n- 角色：{{role_key}}\n- 阶段：投票\n- 性格：{{personality}}\n先回顾讨论重点，再公布投票对象与希望团队执行的后续动作。')
ON DUPLICATE KEY UPDATE `content_template` = VALUES(`content_template`);
