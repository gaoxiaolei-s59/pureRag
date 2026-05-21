

package org.puregxl.site.infra.embedding;


import okhttp3.OkHttpClient;
import org.puregxl.site.infra.enums.ModelProvider;
import org.springframework.stereotype.Service;

@Service
public class SiliconFlowEmbeddingClient extends AbstractOpenAIStyleEmbeddingClient {

    public SiliconFlowEmbeddingClient(OkHttpClient syncHttpClient) {
        super(syncHttpClient);
    }

    @Override
    public String provider() {
        return ModelProvider.SILICON_FLOW.getId();
    }

    @Override
    protected int maxBatchSize() {
        return 32;
    }
}
