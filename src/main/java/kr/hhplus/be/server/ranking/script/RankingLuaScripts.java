package kr.hhplus.be.server.ranking.script;

import org.springframework.stereotype.Component;

/**
 * Redis Lua 스크립트 모음
 * 원자적 연산을 보장하기 위한 스크립트들
 */
@Component
public class RankingLuaScripts {

    /**
     * 예약 카운터 증가 및 실시간 속도 업데이트 스크립트
     */
    public static final String UPDATE_BOOKING_SPEED_SCRIPT = """
            local concertId = ARGV[1]
            local currentMinute = ARGV[2]
            local currentTimestamp = ARGV[3]
            
            -- 예약 카운터 키
            local countKey = 'concert:booking:count:' .. concertId .. ':' .. currentMinute
            local speedKey = 'concert:booking:speed:' .. concertId
            
            -- 예약 카운터 증가
            local count = redis.call('INCR', countKey)
            redis.call('EXPIRE', countKey, 3600)
            
            -- 실시간 속도 업데이트
            redis.call('ZADD', speedKey, currentTimestamp, count)
            
            -- 5분 이전 데이터 삭제
            local fiveMinutesAgo = currentTimestamp - 300
            redis.call('ZREMRANGEBYSCORE', speedKey, 0, fiveMinutesAgo)
            
            return count
            """;

    /**
     * 종합 인기도 점수 계산 및 업데이트 스크립트
     */
    public static final String UPDATE_POPULARITY_SCRIPT = """
            local concertId = ARGV[1]
            local bookingSpeedWeight = tonumber(ARGV[2]) or 0.4
            local soldOutSpeedWeight = tonumber(ARGV[3]) or 0.6
            
            -- 키 정의
            local soldOutKey = 'concert:ranking:soldout_speed'
            local speedKey = 'concert:booking:speed:' .. concertId
            local popularityKey = 'concert:ranking:popularity'
            
            -- 매진 속도 점수 조회
            local soldOutScore = redis.call('ZSCORE', soldOutKey, concertId)
            if not soldOutScore then
                soldOutScore = 0
            end
            
            -- 실시간 예약 속도 계산 (최근 5분)
            local currentTime = redis.call('TIME')[1]
            local fiveMinutesAgo = currentTime - 300
            local speedData = redis.call('ZRANGEBYSCORE', speedKey, fiveMinutesAgo, currentTime, 'WITHSCORES')
            
            local totalBookings = 0
            for i = 2, #speedData, 2 do
                totalBookings = totalBookings + tonumber(speedData[i])
            end
            
            local bookingSpeedPerMinute = totalBookings / 5
            local bookingSpeedScore = bookingSpeedPerMinute * 100
            
            -- 종합 인기도 점수 계산
            local popularityScore = (soldOutScore * soldOutSpeedWeight) + (bookingSpeedScore * bookingSpeedWeight)
            
            -- 랭킹 업데이트
            redis.call('ZADD', popularityKey, popularityScore, concertId)
            
            return {popularityScore, soldOutScore, bookingSpeedScore}
            """;

    /**
     * 랭킹 상세 정보 조회 스크립트
     */
    public static final String GET_RANKING_DETAILS_SCRIPT = """
            local concertId = ARGV[1]
            
            -- 키 정의
            local soldOutKey = 'concert:ranking:soldout_speed'
            local popularityKey = 'concert:ranking:popularity'
            local speedKey = 'concert:booking:speed:' .. concertId
            local infoKey = 'concert:info:' .. concertId
            local statsKey = 'concert:stats:' .. concertId
            
            -- 점수 조회
            local soldOutScore = redis.call('ZSCORE', soldOutKey, concertId)
            local popularityScore = redis.call('ZSCORE', popularityKey, concertId)
            local soldOutRank = redis.call('ZREVRANK', soldOutKey, concertId)
            local popularityRank = redis.call('ZREVRANK', popularityKey, concertId)
            
            -- 실시간 예약 속도 계산
            local currentTime = redis.call('TIME')[1]
            local fiveMinutesAgo = currentTime - 300
            local speedData = redis.call('ZRANGEBYSCORE', speedKey, fiveMinutesAgo, currentTime, 'WITHSCORES')
            
            local totalBookings = 0
            for i = 2, #speedData, 2 do
                totalBookings = totalBookings + tonumber(speedData[i])
            end
            local bookingSpeedPerMinute = totalBookings / 5
            
            -- 콘서트 정보 조회
            local concertInfo = redis.call('HGETALL', infoKey)
            local concertStats = redis.call('HGETALL', statsKey)
            
            return {
                soldOutScore or 0,
                popularityScore or 0,
                soldOutRank,
                popularityRank,
                bookingSpeedPerMinute,
                concertInfo,
                concertStats
            }
            """;
}
