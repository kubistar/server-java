package kr.hhplus.be.server.point.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class BalanceChargeRequestDto {
    private Long amount;
}