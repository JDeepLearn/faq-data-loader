package io.github.jdeeplearn.rag.data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Domain model (immutable) with minimal validation.
 * No persistence concerns leak into this class.
 */
public record Faq(
        @NotBlank @Size(max = 300) String question,
        @NotBlank @Size(max = 1000) String answer,
        String image,   // optional URL
        String link     // optional URL
) {}