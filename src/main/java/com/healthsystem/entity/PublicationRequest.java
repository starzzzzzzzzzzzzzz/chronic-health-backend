package com.healthsystem.entity;
import com.baomidou.mybatisplus.annotation.*;
import java.time.*;
@TableName("publication_request")
public class PublicationRequest extends ScopedEntity {
 @TableField(value="idempotency_key",updateStrategy=FieldStrategy.ALWAYS) private String idempotencyKey;
 public String getIdempotencyKey(){return idempotencyKey;} public void setIdempotencyKey(String value){idempotencyKey=value;}
 @TableField(value="request_hash",updateStrategy=FieldStrategy.ALWAYS) private String requestHash;
 public String getRequestHash(){return requestHash;} public void setRequestHash(String value){requestHash=value;}
 @TableField(value="publication_id",updateStrategy=FieldStrategy.ALWAYS) private String publicationId;
 public String getPublicationId(){return publicationId;} public void setPublicationId(String value){publicationId=value;}
}
