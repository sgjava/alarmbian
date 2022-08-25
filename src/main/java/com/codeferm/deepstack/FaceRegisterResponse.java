package com.codeferm.deepstack;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@ToString
@NoArgsConstructor
@JsonDeserialize
public class FaceRegisterResponse extends Response {

    String message;

}
