package lingzhou.agent.backend.capability.rag.embedding;

import java.util.ArrayList;
import java.util.List;
import lingzhou.agent.backend.business.datasets.domain.DocumentChunk;
import lingzhou.agent.backend.capability.rag.chunk.tool.TableChunkContentSupport;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Service;

@Service
public class DocumentEmbeddingService {

    private static final int MAX_BATCH_SIZE = 10;

    private final EmbeddingModel embeddingModel;

    public DocumentEmbeddingService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public List<VectorizedChunk> embedChunks(List<DocumentChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }

        List<VectorizedChunk> vectorizedChunks = new ArrayList<>(chunks.size());
        for (int start = 0; start < chunks.size(); start += MAX_BATCH_SIZE) {
            int end = Math.min(start + MAX_BATCH_SIZE, chunks.size());
            List<DocumentChunk> batchChunks = chunks.subList(start, end);
            List<String> texts = batchChunks.stream()
                    .map(chunk -> TableChunkContentSupport.toPlainText(chunk.getChunkType(), chunk.getChunkContent()))
                    .toList();
            EmbeddingResponse response = embeddingModel.call(new EmbeddingRequest(texts, null));
            List<Embedding> embeddings = response.getResults();
            if (embeddings.size() != batchChunks.size()) {
                throw new IllegalStateException("Embedding response size does not match chunk count.");
            }

            for (int i = 0; i < batchChunks.size(); i++) {
                vectorizedChunks.add(new VectorizedChunk(batchChunks.get(i), embeddings.get(i).getOutput()));
            }
        }
        return vectorizedChunks;
    }

    public float[] embedText(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Embedding text must not be blank.");
        }

        EmbeddingResponse response = embeddingModel.call(new EmbeddingRequest(List.of(text), null));
        List<Embedding> embeddings = response.getResults();
        if (embeddings == null || embeddings.isEmpty()) {
            throw new IllegalStateException("Embedding response is empty.");
        }
        return embeddings.get(0).getOutput();
    }

    public record VectorizedChunk(DocumentChunk chunk, float[] vector) {}
}
