package com.example.demo.entity;

import lombok.Data;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.time.LocalDateTime;

@Node("Post")
@Data
public class PostNode {
    @Id
    private String mid;
    private String rootMid;
    private String screenName;
    private String content;
    private Integer repostCount;
    private Integer sentiment;
    private LocalDateTime postTime;

    // 当前节点 -> 父节点（被转发的微博）
    @Relationship(type = "REPOSTED_FROM", direction = Relationship.Direction.OUTGOING)
    private PostNode parent;
}