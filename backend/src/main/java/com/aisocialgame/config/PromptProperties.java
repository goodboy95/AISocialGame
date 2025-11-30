package com.aisocialgame.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Central place to manage AI-related prompt templates and word lists.
 * Values are loaded from {@code prompt.yml} so that business code does not hardcode prompts.
 */
@Component
@ConfigurationProperties(prefix = "prompts")
public class PromptProperties {

    private AiName aiName = new AiName();
    private AiTalk aiTalk = new AiTalk();

    public AiName getAiName() {
        return aiName;
    }

    public void setAiName(AiName aiName) {
        this.aiName = aiName;
    }

    public AiTalk getAiTalk() {
        return aiTalk;
    }

    public void setAiTalk(AiTalk aiTalk) {
        this.aiTalk = aiTalk;
    }

    public static class AiName {
        private static final String DEFAULT_REMOTE_PROMPT = "你是社交推理游戏的取名助手，根据人物设定和风格生成 2-6 个字的中文昵称，不要带标点或空格，仅返回昵称本身。";
        private static final List<String> DEFAULT_ADJECTIVES = List.of(
                "机敏的", "神秘的", "俏皮的", "沉稳的", "勇敢的",
                "灵光一闪的", "幽默的", "谨慎的", "率真的", "冷静的"
        );

        private static final List<String> DEFAULT_SUFFIXES = List.of(
                "玩家", "侦探", "旅人", "观察家", "解谜人",
                "吟游者", "思考者", "夜行者", "梦者", "挑战者"
        );

        private String remotePrompt = DEFAULT_REMOTE_PROMPT;
        private List<String> adjectives = DEFAULT_ADJECTIVES;
        private List<String> suffixes = DEFAULT_SUFFIXES;

        public String getRemotePrompt() {
            return StringUtils.hasText(remotePrompt) ? remotePrompt : DEFAULT_REMOTE_PROMPT;
        }

        public void setRemotePrompt(String remotePrompt) {
            this.remotePrompt = remotePrompt;
        }

        public List<String> getAdjectives() {
            return CollectionUtils.isEmpty(adjectives) ? DEFAULT_ADJECTIVES : adjectives;
        }

        public void setAdjectives(List<String> adjectives) {
            this.adjectives = adjectives;
        }

        public List<String> getSuffixes() {
            return CollectionUtils.isEmpty(suffixes) ? DEFAULT_SUFFIXES : suffixes;
        }

        public void setSuffixes(List<String> suffixes) {
            this.suffixes = suffixes;
        }
    }

    public static class AiTalk {
        private static final String DEFAULT_DESCRIPTION_TEMPLATE = "%s，我觉得这个词很特别。";
        private static final String DEFAULT_SUSPICION_TEMPLATE = "我觉得%s号有问题";
        private static final String DEFAULT_VOTE_LOG_TEMPLATE = "%s（AI）已完成投票";

        private String descriptionTemplate = DEFAULT_DESCRIPTION_TEMPLATE;
        private String suspicionTemplate = DEFAULT_SUSPICION_TEMPLATE;
        private String voteLogTemplate = DEFAULT_VOTE_LOG_TEMPLATE;

        public String getDescriptionTemplate() {
            return StringUtils.hasText(descriptionTemplate) ? descriptionTemplate : DEFAULT_DESCRIPTION_TEMPLATE;
        }

        public void setDescriptionTemplate(String descriptionTemplate) {
            this.descriptionTemplate = descriptionTemplate;
        }

        public String getSuspicionTemplate() {
            return StringUtils.hasText(suspicionTemplate) ? suspicionTemplate : DEFAULT_SUSPICION_TEMPLATE;
        }

        public void setSuspicionTemplate(String suspicionTemplate) {
            this.suspicionTemplate = suspicionTemplate;
        }

        public String getVoteLogTemplate() {
            return StringUtils.hasText(voteLogTemplate) ? voteLogTemplate : DEFAULT_VOTE_LOG_TEMPLATE;
        }

        public void setVoteLogTemplate(String voteLogTemplate) {
            this.voteLogTemplate = voteLogTemplate;
        }
    }
}
