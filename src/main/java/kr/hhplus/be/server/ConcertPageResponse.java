package kr.hhplus.be.server;


import kr.hhplus.be.server.dto.ConcertResponseDto;

import java.util.List;

public class ConcertPageResponse {
    private List<ConcertResponseDto> concerts;
    private PaginationResponse pagination;

    private ConcertPageResponse() {}

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private ConcertPageResponse response = new ConcertPageResponse();

        public Builder concerts(List<ConcertResponseDto> concerts) {
            response.concerts = concerts;
            return this;
        }

        public Builder pagination(PaginationResponse pagination) {
            response.pagination = pagination;
            return this;
        }

        public ConcertPageResponse build() {
            return response;
        }
    }

    public List<ConcertResponseDto> getConcerts() { return concerts; }
    public PaginationResponse getPagination() { return pagination; }
}