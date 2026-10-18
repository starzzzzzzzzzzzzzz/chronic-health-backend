package com.healthsystem.entity;
import com.baomidou.mybatisplus.annotation.*;
import java.time.*;
@TableName("outbox_event")
public class OutboxEvent extends ScopedEntity {
 @TableField(value="publication_id",updateStrategy=FieldStrategy.ALWAYS) private String publicationId;
 public String getPublicationId(){return publicationId;} public void setPublicationId(String value){publicationId=value;}
 @TableField(value="event_type",updateStrategy=FieldStrategy.ALWAYS) private String eventType;
 public String getEventType(){return eventType;} public void setEventType(String value){eventType=value;}
 @TableField(value="status",updateStrategy=FieldStrategy.ALWAYS) private String status;
 public String getStatus(){return status;} public void setStatus(String value){status=value;}
 @TableField(value="payload_json",updateStrategy=FieldStrategy.ALWAYS) private String payloadJson;
 public String getPayloadJson(){return payloadJson;} public void setPayloadJson(String value){payloadJson=value;}
}
