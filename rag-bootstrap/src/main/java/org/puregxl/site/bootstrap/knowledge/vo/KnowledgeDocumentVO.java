package org.puregxl.site.bootstrap.knowledge.vo;

import lombok.Data;

import java.util.Date;

@Data
public class KnowledgeDocumentVO {

    private String id;

    private String kbId;

    private String docName;

    private String sourceType;

    private String sourceLocation;

    private Integer enabled;

    private Integer chunkCount;

    private String fileType;

    private Long fileSize;

    private String status;

    private Date createTime;

    private Date updateTime;
}
