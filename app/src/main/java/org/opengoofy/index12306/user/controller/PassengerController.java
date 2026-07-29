package org.opengoofy.index12306.user.controller;

import lombok.RequiredArgsConstructor;
import org.opengoofy.index12306.common.core.UserContext;
import org.opengoofy.index12306.framework.convention.result.Result;
import org.opengoofy.index12306.framework.convention.result.Results;
import org.opengoofy.index12306.user.dto.req.PassengerRemoveReqDTO;
import org.opengoofy.index12306.user.dto.req.PassengerReqDTO;
import org.opengoofy.index12306.user.dto.resp.PassengerActualRespDTO;
import org.opengoofy.index12306.user.dto.resp.PassengerRespDTO;
import org.opengoofy.index12306.user.service.PassengerService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 乘车人控制层。
 */
@RestController
@RequiredArgsConstructor
public class PassengerController {

    private final PassengerService passengerService;

    @GetMapping("/api/user-service/passenger/query")
    public Result<List<PassengerRespDTO>> listPassenger() {
        return Results.success(passengerService.listPassenger(UserContext.getUsername()));
    }

    @GetMapping("/api/user-service/inner/passenger/actual/query/ids")
    public Result<List<PassengerActualRespDTO>> listPassengerActualByIds(@RequestParam("username") String username,
                                                                         @RequestParam("ids") List<String> ids) {
        return Results.success(passengerService.listPassengerActual(username, ids));
    }

    @PostMapping("/api/user-service/passenger/save")
    public Result<Void> savePassenger(@RequestBody PassengerReqDTO requestParam) {
        passengerService.savePassenger(requestParam);
        return Results.success();
    }

    @PostMapping("/api/user-service/passenger/update")
    public Result<Void> updatePassenger(@RequestBody PassengerReqDTO requestParam) {
        passengerService.updatePassenger(requestParam);
        return Results.success();
    }

    @PostMapping("/api/user-service/passenger/remove")
    public Result<Void> removePassenger(@RequestBody PassengerRemoveReqDTO requestParam) {
        passengerService.removePassenger(requestParam);
        return Results.success();
    }
}