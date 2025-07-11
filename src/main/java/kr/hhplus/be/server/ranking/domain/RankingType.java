package kr.hhplus.be.server.ranking.domain;

public enum RankingType {
    SOLDOUT_SPEED("매진속도"),
    BOOKING_SPEED("예약속도"),
    POPULARITY("종합인기도");

    private final String description;

    RankingType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}