/**
 * 
 */
package logbook.dto;

import logbook.data.DataType;

/**
 * @author Nekopanda
 *
 */
public enum BattlePhaseKind {

    /** 通常の昼戦 */
    BATTLE(false, BattlePatternConstants.NON_COMBINED_PTTERN, DataType.BATTLE),
    /** 通常の夜戦 */
    MIDNIGHT(true, BattlePatternConstants.NON_COMBINED_PTTERN, DataType.BATTLE_MIDNIGHT),
    /** 演習の昼戦 */
    PRACTICE_BATTLE(false, BattlePatternConstants.NON_COMBINED_PTTERN, DataType.PRACTICE_BATTLE),
    /** 演習の夜戦 */
    PRACTICE_MIDNIGHT(true, BattlePatternConstants.NON_COMBINED_PTTERN, DataType.PRACTICE_BATTLE_MIDNIGHT),
    /** 夜戦マスの戦闘 */
    SP_MIDNIGHT(true, BattlePatternConstants.NON_COMBINED_PTTERN, DataType.BATTLE_SP_MIDNIGHT),
    /** 夜戦→昼戦マスの昼戦 */
    NIGHT_TO_DAY(false, BattlePatternConstants.NON_COMBINED_PTTERN, DataType.BATTLE_NIGHT_TO_DAY),
    /** 連合艦隊空母機動部隊の昼戦 */
    COMBINED_BATTLE(false, BattlePatternConstants.BATTLE_PATTERN, DataType.COMBINED_BATTLE),
    /** 連合艦隊航空戦マス */
    COMBINED_AIR(false, BattlePatternConstants.BATTLE_PATTERN, DataType.COMBINED_AIR_BATTLE),
    /** 連合艦隊の夜戦 */
    COMBINED_MIDNIGHT(true, BattlePatternConstants.BATTLE_PATTERN, DataType.COMBINED_BATTLE_MIDNIGHT),
    /** 連合艦隊での夜戦マスの戦闘 */
    COMBINED_SP_MIDNIGHT(true, BattlePatternConstants.BATTLE_PATTERN, DataType.COMBINED_BATTLE_SP_MIDNIGHT),
    /** 連合艦隊水上打撃部隊の昼戦 */
    COMBINED_BATTLE_WATER(false, BattlePatternConstants.WATER_PATTERN, DataType.COMBINED_BATTLE_WATER);

    private final boolean night;
    private final boolean[] pattern;
    private final DataType api;

    private BattlePhaseKind(boolean night, boolean[] pattern, DataType api) {
        this.night = night;
        this.pattern = pattern;
        this.api = api;
    }

    /**
     * 夜戦か？
     * @return night
     */
    public boolean isNight() {
        return this.night;
    }

    /**
     * 開幕戦は第二艦隊が行うか？
     * @return
     */
    public boolean isOpeningSecond() {
        return this.pattern[0];
    }

    /**
     * 夜戦は第二艦隊が行うか？
     * @return
     */
    public boolean isHougekiSecond() {
        return this.pattern[1];
    }

    /**
     * 砲撃戦1は第二艦隊が行うか？
     * @return
     */
    public boolean isHougeki1Second() {
        return this.pattern[2];
    }

    /**
     * 砲撃戦2は第二艦隊が行うか？
     * @return
     */
    public boolean isHougeki2Second() {
        return this.pattern[3];
    }

    /**
     * 砲撃戦3は第二艦隊が行うか？
     * @return
     */
    public boolean isHougeki3Second() {
        return this.pattern[4];
    }

    /**
     * 雷撃戦は第二艦隊が行うか？
     * @return
     */
    public boolean isRaigekiSecond() {
        return this.pattern[5];
    }

    /**
     * この戦闘のAPIリクエスト先
     * @return api
     */
    public DataType getApi() {
        return this.api;
    }
}

class BattlePatternConstants {
    // 第一->false, 第二->true
    // opening, hougeki, hougeki1, hougeki2, hougeki3, raigeki

    // 通常戦闘
    public static boolean[] NON_COMBINED_PTTERN = new boolean[] {
            false, false, false, false, false, false
    };

    // 連合艦隊の通常
    public static boolean[] BATTLE_PATTERN = new boolean[] {
            true, true, true, false, false, true
    };

    // 連合艦隊の水雷戦
    public static boolean[] WATER_PATTERN = new boolean[] {
            true, true, false, false, true, true
    };
}
