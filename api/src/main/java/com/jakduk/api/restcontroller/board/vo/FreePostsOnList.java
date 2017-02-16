package com.jakduk.api.restcontroller.board.vo;

import com.jakduk.core.common.CoreConst;
import com.jakduk.core.model.embedded.BoardImage;
import com.jakduk.core.model.embedded.BoardStatus;
import com.jakduk.core.model.embedded.CommonWriter;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * @author pyohwan
 *         16. 7. 11 오후 9:25
 */

@ApiModel(value = "자유게시판 게시물")
@NoArgsConstructor
@Getter
@Setter
public class FreePostsOnList {

    @ApiModelProperty(value = "글ID")
    private String id;

    @ApiModelProperty(value = "글쓴이")
    private CommonWriter writer;

    @ApiModelProperty(value = "글제목")
    private String subject;

    @ApiModelProperty(value = "글번호")
    private int seq;

    @ApiModelProperty(value = "말머리")
    private CoreConst.BOARD_CATEGORY_TYPE category;

    @ApiModelProperty(value = "읽음 수")
    private int views;

    @ApiModelProperty(value = "글상태")
    private BoardStatus status;

    @ApiModelProperty(value = "그림 목록")
    private List<BoardImage> galleries;

    @ApiModelProperty(value = "댓글 수")
    private int commentCount;

    @ApiModelProperty(value = "좋아요 수")
    private int likingCount;

    @ApiModelProperty(value = "싫어요 수")
    private int dislikingCount;

}
