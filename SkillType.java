package vn.astrax.skill;

public enum SkillType {
    // Kiếm
    FLAME("Hỏa Diễm", false),
    LIGHTNING("Lôi Kích", false),
    LIFESTEAL("Hút Huyết", false),
    FREEZE("Băng Giá", false),
    // Giáp
    GUARD("Hộ Thể", true),
    REFLECT("Phản Chấn", true),
    REGEN("Hồi Sinh", true);

    public final String display;
    public final boolean armor;

    SkillType(String display, boolean armor) {
        this.display = display;
        this.armor = armor;
    }

    public static SkillType from(String s) {
        if (s == null) return null;
        try {
            return valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
