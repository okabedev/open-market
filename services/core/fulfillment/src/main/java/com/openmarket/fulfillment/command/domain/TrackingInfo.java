package com.openmarket.fulfillment.command.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.util.Arrays;
import java.util.regex.Pattern;

/** 송장(docs/glossary.md "송장"). 택배사와 송장 번호다. */
@Embeddable
public record TrackingInfo(
        @Enumerated(EnumType.STRING) @Column(name = "carrier") Carrier carrier,
        @Column(name = "tracking_number") String trackingNumber) {

    private static final Pattern TRACKING_NUMBER = Pattern.compile("[0-9]{10,14}");

    /** 판매자 입력으로 만든다. 택배사 코드가 없거나 송장 번호가 숫자 10~14자리가 아니면 400 {@code FULFILLMENT_INVALID_INPUT}. */
    public static TrackingInfo of(String carrier, String trackingNumber) {
        Carrier parsed =
                Arrays.stream(Carrier.values())
                        .filter(value -> value.name().equals(carrier))
                        .findFirst()
                        .orElseThrow(() -> new InvalidFulfillmentInputException("지원하지 않는 택배사입니다."));
        if (!TRACKING_NUMBER.matcher(trackingNumber).matches()) {
            throw new InvalidFulfillmentInputException("송장 번호는 하이픈 없이 숫자 10~14자리입니다.");
        }
        return new TrackingInfo(parsed, trackingNumber);
    }
}
