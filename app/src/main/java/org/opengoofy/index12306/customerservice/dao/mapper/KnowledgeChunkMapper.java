package org.opengoofy.index12306.customerservice.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.opengoofy.index12306.customerservice.dao.entity.KnowledgeChunkDO;
import org.opengoofy.index12306.customerservice.rag.ChunkSearchResult;

import java.util.List;

/**
 * 知识切片 Mapper，继承 MyBatis-Plus BaseMapper 并提供向量/关键词检索方法。
 */
@Mapper
public interface KnowledgeChunkMapper extends BaseMapper<KnowledgeChunkDO> {

    /**
     * 向量检索：pgvector 余弦相似度（HNSW 索引），返回相似度降序排列的切片。
     *
     * @param queryVec  查询向量字面量（"[0.1,0.2,...]" 格式，由 VectorTypeHandler.toLiteral 生成）
     * @param categoryId 分类 ID，null 表示不限分类
     * @param topK      最大返回数量
     * @return 检索结果列表，score 为余弦相似度
     */
    @Select("SELECT id, category_id, document_id, chunk_index, content, metadata, " +
            "1 - (embedding <=> CAST(#{queryVec} AS vector)) AS score " +
            "FROM knowledge_chunk " +
            "WHERE (CAST(#{categoryId} AS BIGINT) IS NULL OR category_id = CAST(#{categoryId} AS BIGINT)) " +
            "ORDER BY embedding <=> CAST(#{queryVec} AS vector) " +
            "LIMIT #{topK}")
    List<ChunkSearchResult> vectorSearch(@Param("queryVec") String queryVec,
                                         @Param("categoryId") Long categoryId,
                                         @Param("topK") int topK);

    /**
     * 关键词检索：ILIKE 模糊匹配（pg_trgm GIN 索引加速），适合精确字段查询。
     *
     * @param query     查询关键词
     * @param categoryId 分类 ID，null 表示不限分类
     * @param topK      最大返回数量
     * @return 检索结果列表
     */
    @Select("SELECT id, category_id, document_id, chunk_index, content, metadata " +
            "FROM knowledge_chunk " +
            "WHERE (CAST(#{categoryId} AS BIGINT) IS NULL OR category_id = CAST(#{categoryId} AS BIGINT)) " +
            "AND content ILIKE CONCAT('%', CAST(#{query} AS TEXT), '%') " +
            "ORDER BY id " +
            "LIMIT #{topK}")
    List<ChunkSearchResult> keywordSearch(@Param("query") String query,
                                          @Param("categoryId") Long categoryId,
                                          @Param("topK") int topK);

    /** 按分词后的词项召回，使用 strpos 避免 % 和 _ 被解释为通配符。 */
    @Select({"<script>",
            "SELECT id, category_id, document_id, chunk_index, content, metadata FROM knowledge_chunk",
            "WHERE (CAST(#{categoryId} AS BIGINT) IS NULL OR category_id = CAST(#{categoryId} AS BIGINT))",
            "AND (",
            "<foreach collection='terms' item='term' separator=' OR '>",
            "strpos(lower(content), lower(#{term})) &gt; 0",
            "</foreach>", ") ORDER BY (",
            "<foreach collection='terms' item='term' separator=' + '>",
            "CASE WHEN strpos(lower(content), lower(#{term})) &gt; 0 THEN 1 ELSE 0 END",
            "</foreach>", ") DESC, id LIMIT #{topK}", "</script>"})
    List<ChunkSearchResult> keywordSearchTerms(@Param("terms") List<String> terms,
                                               @Param("categoryId") Long categoryId, @Param("topK") int topK);

    /**
     * 查询命中切片的上下文窗口，用于 RAG 检索后的邻居扩展。
     */
    @Select("SELECT id, category_id, document_id, chunk_index, content, metadata " +
            "FROM knowledge_chunk " +
            "WHERE document_id = #{documentId} " +
            "AND chunk_index BETWEEN #{startIndex} AND #{endIndex} " +
            "ORDER BY chunk_index")
    List<ChunkSearchResult> selectContextWindow(@Param("documentId") Long documentId,
                                                @Param("startIndex") int startIndex,
                                                @Param("endIndex") int endIndex);
}
