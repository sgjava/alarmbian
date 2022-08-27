package com.codeferm.deepstack;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@ToString
@NoArgsConstructor
@JsonDeserialize
@JsonIgnoreProperties(ignoreUnknown = true)
public class Prediction {

    @JsonProperty("label")
    String label;
    @JsonProperty("userid")
    String userId;
    @JsonProperty("confidence")
    double confidence;
    @JsonProperty("y_min")
    int yMin;
    @JsonProperty("x_min")
    int xMin;
    @JsonProperty("y_max")
    int yMax;
    @JsonProperty("x_max")
    int xMax;

}
