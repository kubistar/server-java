package kr.hhplus.be.server;

import org.springframework.data.domain.Page;

public class PaginationResponse {
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;

    private PaginationResponse() {}

    public static PaginationResponse from(Page<?> page) {
        PaginationResponse response = new PaginationResponse();
        response.page = page.getNumber();
        response.size = page.getSize();
        response.totalElements = page.getTotalElements();
        response.totalPages = page.getTotalPages();
        return response;
    }

    public int getPage() { return page; }
    public int getSize() { return size; }
    public long getTotalElements() { return totalElements; }
    public int getTotalPages() { return totalPages; }
}