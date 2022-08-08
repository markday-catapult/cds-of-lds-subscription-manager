package com.catapult.lds.service;

import lombok.Data;
import lombok.extern.jackson.Jacksonized;

import java.util.List;

@Data
@Jacksonized
public class Resource {
    private List<String> athleteId;
    private List<String> deviceId;
}
