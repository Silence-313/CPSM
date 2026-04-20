package com.example.demo.repository;

import com.example.demo.entity.PostNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface RepostNeo4jRepository extends Neo4jRepository<PostNode, String> {

    @Query("MATCH (p:Post {rootMid: $rootMid}) RETURN p")
    List<PostNode> findAllNodesByRootMid(String rootMid);

    @Query("MATCH path = (root:Post {mid: $mid})<-[:REPOSTED_FROM*0..10]-(child:Post) RETURN nodes(path) AS nodes, relationships(path) AS rels")
    List<Object> findTreeByRootMid(String mid);
}