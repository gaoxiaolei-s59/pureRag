package org.puregxl.site.rag.core.intent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NodeScore {
    private double score;

    private IntentNode intentNode;
}
