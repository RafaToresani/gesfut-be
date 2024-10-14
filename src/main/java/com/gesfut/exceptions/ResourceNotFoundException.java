package com.gesfut.exceptions;

import lombok.*;

@Getter
@Setter
@Builder
@AllArgsConstructor
public class ResourceNotFoundException extends RuntimeException{
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
