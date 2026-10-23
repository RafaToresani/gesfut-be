package com.gesfut.dtos.responses;

import java.util.List;

/** Plan 22/10 #15: resultado del import de Excel: equipos creados y los que fallaron (con el motivo). */
public record TeamImportResponse(
        int createdCount,
        List<String> created,
        List<Failure> failed
) {
    public record Failure(int index, String name, String error) {}
}
