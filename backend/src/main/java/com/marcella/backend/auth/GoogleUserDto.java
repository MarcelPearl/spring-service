package com.marcella.backend.auth;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GoogleUserDto {
    private String email;
    private String name;
    private String profilePicture;
}

