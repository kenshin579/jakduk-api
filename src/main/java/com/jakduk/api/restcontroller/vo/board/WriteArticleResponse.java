package com.jakduk.api.restcontroller.vo.board;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * @author pyohwan
 *         16. 7. 18 오후 9:19
 */

@ApiModel("자유게시판 글쓰기 & 글 편집")
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class WriteArticleResponse {

    @ApiModelProperty(example = "FREE", value = "게시판ID")
    private String board;

    @ApiModelProperty(example = "2", value = "글번호")
    private Integer seq;
}
