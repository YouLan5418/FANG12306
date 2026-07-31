package org.opengoofy.index12306.admin.dto.req;

import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
public class TrainPublishReqDTO {

    private String trainNumber;
    private Integer trainType;
    private String trainTag;
    private String trainBrand;
    private Date saleTime;
    private Integer saleStatus;
    private List<StationSpec> stations;
    private List<SeatPriceSpec> seatPrices;
    private List<CarriageSpec> carriages;

    @Data
    public static class StationSpec {
        private Long stationId;
        private String stationName;
        private String region;
        private Date arrivalTime;
        private Date departureTime;
        private Integer stopoverTime;
    }

    @Data
    public static class SeatPriceSpec {
        private Integer seatType;
        private Integer price;
    }

    @Data
    public static class CarriageSpec {
        private String carriageNumber;
        private Integer carriageType;
        private Integer seatType;
        private Integer seatCount;
    }
}
