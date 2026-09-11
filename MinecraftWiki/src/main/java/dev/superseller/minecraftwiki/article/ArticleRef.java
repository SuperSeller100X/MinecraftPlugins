package dev.superseller.minecraftwiki.article;

/**
 * A reference from an article to another Minecraft object.
 *
 * <p>References are rendered as interactive entries: items show their real icon, entities
 * and articles link to their own page, commands can be suggested into chat, recipes open
 * the recipe viewer and tags list their values.</p>
 */
public record ArticleRef(RefType type, String id, String label) {

    public enum RefType {
        ITEM("Item"),
        BLOCK("Block"),
        ENTITY("Entity"),
        COMMAND("Command"),
        RECIPE("Recipe"),
        TAG("Tag"),
        SOUND("Sound"),
        ARTICLE("See also");

        private final String label;

        RefType(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public static ArticleRef item(String material) {
        return new ArticleRef(RefType.ITEM, material, null);
    }

    public static ArticleRef block(String material) {
        return new ArticleRef(RefType.BLOCK, material, null);
    }

    public static ArticleRef entity(String entityType) {
        return new ArticleRef(RefType.ENTITY, entityType, null);
    }

    public static ArticleRef command(String command) {
        return new ArticleRef(RefType.COMMAND, command, null);
    }

    public static ArticleRef recipe(String material) {
        return new ArticleRef(RefType.RECIPE, material, null);
    }

    public static ArticleRef tag(String tagKey) {
        return new ArticleRef(RefType.TAG, tagKey, null);
    }

    public static ArticleRef sound(String soundKey) {
        return new ArticleRef(RefType.SOUND, soundKey, null);
    }

    public static ArticleRef article(ArticleId article) {
        return new ArticleRef(RefType.ARTICLE, article.value(), null);
    }
}
