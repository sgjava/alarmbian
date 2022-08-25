package com.codeferm.deepstack;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.List;

@Getter
@ToString
@NoArgsConstructor
@JsonDeserialize
public class FaceRecognizeResponse extends Response {

    List<Prediction> predictions;

}
