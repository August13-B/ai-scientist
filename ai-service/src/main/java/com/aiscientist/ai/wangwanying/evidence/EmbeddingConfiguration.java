package com.aiscientist.ai.wangwanying.evidence;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallzhv15q.BgeSmallZhV15QuantizedEmbeddingModel;
import org.springframework.context.annotation.*;
@Configuration
public class EmbeddingConfiguration {
    @Bean public EmbeddingModel evidenceEmbeddingModel() { return new BgeSmallZhV15QuantizedEmbeddingModel(); }
}