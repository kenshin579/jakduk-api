package com.jakduk.restcontroller.board;

import com.jakduk.common.ApiConst;
import com.jakduk.common.ApiUtils;
import com.jakduk.common.CommonConst;
import com.jakduk.dao.BoardDAO;
import com.jakduk.exception.ServiceError;
import com.jakduk.exception.ServiceException;
import com.jakduk.model.db.BoardCategory;
import com.jakduk.model.db.BoardFree;
import com.jakduk.model.db.BoardFreeComment;
import com.jakduk.model.db.Gallery;
import com.jakduk.model.embedded.BoardImage;
import com.jakduk.model.embedded.BoardItem;
import com.jakduk.model.etc.BoardFeelingCount;
import com.jakduk.model.etc.BoardFreeOnBest;
import com.jakduk.model.etc.GalleryOnBoard;
import com.jakduk.model.simple.BoardFreeOfMinimum;
import com.jakduk.model.simple.BoardFreeOnList;
import com.jakduk.model.simple.BoardFreeOnSearchComment;
import com.jakduk.restcontroller.board.vo.*;
import com.jakduk.restcontroller.vo.UserFeelingResponse;
import com.jakduk.service.*;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.mobile.device.Device;
import org.springframework.mobile.device.DeviceUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * @author pyohwan
 * 16. 3. 26 오후 11:05
 */

@Slf4j
@Api(tags = "게시판", description = "게시판 관련")
@RestController
@RequestMapping("/api/board")
public class BoardRestController {

    @Autowired
    private BoardFreeService boardFreeService;

    @Autowired
    private BoardCategoryService boardCategoryService;

    @Autowired
    private UserService userService;

    @Autowired
    private CommonService commonService;

    @Autowired
    private GalleryService galleryService;

    @Autowired
    private BoardDAO boardDAO;

    @ApiOperation(value = "자유게시판 글 목록", produces = "application/json", response = FreePostsOnListResponse.class)
    @RequestMapping(value = "/free/posts", method = RequestMethod.GET)
    public FreePostsOnListResponse getFreePosts(@RequestParam(required = false, defaultValue = "1") Integer page,
                                            @RequestParam(required = false, defaultValue = "20") Integer size,
                                            @RequestParam(required = false, defaultValue = "ALL") CommonConst.BOARD_CATEGORY_TYPE category) {

        if (Objects.isNull(category))
            category = CommonConst.BOARD_CATEGORY_TYPE.ALL;

        Page<BoardFreeOnList> posts = boardFreeService.getFreePosts(category, page, size);
        Page<BoardFreeOnList> notices = boardFreeService.getFreeNotices();

        ArrayList<Integer> seqs = new ArrayList<>();
        ArrayList<ObjectId> ids = new ArrayList<>();

        // id와 seq 뽑아내기.
        Consumer<BoardFreeOnList> extractIdAndSeq = board -> {
            String tempId = board.getId();
            Integer tempSeq = board.getSeq();

            ObjectId objId = new ObjectId(tempId);

            seqs.add(tempSeq);
            ids.add(objId);
        };

        posts.getContent().forEach(extractIdAndSeq);
        notices.getContent().forEach(extractIdAndSeq);

        Map<String, Integer> commentCounts = boardDAO.getBoardFreeCommentCount(seqs);
        Map<String, BoardFeelingCount> feelingCounts = boardDAO.getBoardFreeUsersFeelingCount(ids);

        // 댓글수, 감정 표현수 합치기.
        Consumer<FreePostsOnList> applyCounts = board -> {
            String tempId = board.getId();

            Integer commentCount = commentCounts.get(tempId);

            if (Objects.nonNull(commentCount))
                board.setCommentCount(commentCount);

            BoardFeelingCount feelingCount = feelingCounts.get(tempId);

            if (Objects.nonNull(feelingCount)) {
                board.setLikingCount(feelingCount.getUsersLikingCount());
                board.setDislikingCount(feelingCount.getUsersDisLikingCount());
            }
        };

        List<FreePostsOnList> freePosts = posts.getContent().stream()
                .map(FreePostsOnList::new)
                .collect(Collectors.toList());

        freePosts.forEach(applyCounts);

        List<FreePostsOnList> freeNotices = notices.getContent().stream()
                .map(FreePostsOnList::new)
                .collect(Collectors.toList());

        freeNotices.forEach(applyCounts);

        List<BoardCategory> categories = boardFreeService.getFreeCategories();
        Map<String, String> categoriesMap = categories.stream().collect(Collectors.toMap(BoardCategory::getCode, boardCategory -> boardCategory.getNames().get(0).getName()));
        categoriesMap.put("ALL", commonService.getResourceBundleMessage("messages.board", "board.category.all"));

        return FreePostsOnListResponse.builder()
                .categories(categoriesMap)
                .posts(freePosts)
                .notices(freeNotices)
                .first(posts.isFirst())
                .last(posts.isLast())
                .totalPages(posts.getTotalPages())
                .totalElements(posts.getTotalElements())
                .numberOfElements(posts.getNumberOfElements())
                .size(posts.getSize())
                .number(posts.getNumber())
                .build();
    }

    @ApiOperation(value = "자유게시판 주간 선두 글", produces = "application/json", response = FreeTopsResponse.class)
    @RequestMapping(value = "/free/tops", method = RequestMethod.GET)
    public FreeTopsResponse getFreePostsTops() {

        List<BoardFreeOnBest> topLikes = boardFreeService.getFreeTopLikes();
        List<BoardFreeOnBest> topComments = boardFreeService.getFreeTopComments();

        return FreeTopsResponse.builder()
                .topLikes(topLikes)
                .topComments(topComments)
                .build();
    }

    @ApiOperation(value = "자유게시판 댓글 목록", produces = "application/json", response = FreeCommentsOnListResponse.class)
    @RequestMapping(value = "/free/comments", method = RequestMethod.GET)
    public FreeCommentsOnListResponse getFreeComments(
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {

        Page<BoardFreeComment> comments = boardFreeService.getBoardFreeComments(page, size);

        List<ObjectId> boardIds = new ArrayList<>();

        // id 뽑아내기.
        Consumer<BoardFreeComment> extractId = comment -> {
            String tempId = comment.getBoardItem().getId();
            ObjectId objId = new ObjectId(tempId);
            boardIds.add(objId);
        };

        comments.getContent().forEach(extractId);

        List<FreeCommentsOnList> freeComments = comments.getContent().stream()
                .map(FreeCommentsOnList::new)
                .collect(Collectors.toList());

        Map<String, BoardFreeOnSearchComment> postsHavingComments = boardDAO.getBoardFreeOnSearchComment(boardIds);

        // 글 정보 합치기.
        Consumer<FreeCommentsOnList> applyPosts = comment -> {
            String tempBoardId = comment.getBoardItem().getId();

            BoardFreeOnSearchComment tempBoardItem = postsHavingComments.get(tempBoardId);

            if (Objects.nonNull(tempBoardItem))
                comment.setBoardItem(tempBoardItem);
        };

        freeComments.forEach(applyPosts);

        return FreeCommentsOnListResponse.builder()
                .comments(freeComments)
                .first(comments.isFirst())
                .last(comments.isLast())
                .totalPages(comments.getTotalPages())
                .totalElements(comments.getTotalElements())
                .numberOfElements(comments.getNumberOfElements())
                .size(comments.getSize())
                .number(comments.getNumber())
                .build();
    }

    @ApiOperation(value = "자유게시판 글 상세", produces = "application/json", response = FreePostOnDetailResponse.class)
    @RequestMapping(value = "/free/{seq}", method = RequestMethod.GET)
    public FreePostOnDetailResponse getFreeView(@PathVariable Integer seq,
                                                HttpServletRequest request,
                                                HttpServletResponse response) {

        Optional<BoardFree> boardFree = boardFreeService.getFreePost(seq);

        if (!boardFree.isPresent())
            throw new ServiceException(ServiceError.POST_NOT_FOUND);

        BoardFree getBoardFree = boardFree.get();
        boolean isAddCookie = ApiUtils.addViewsCookie(request, response, ApiConst.COOKIE_VIEWS_TYPE.FREE_BOARD, String.valueOf(seq));

        if (isAddCookie) {
            int views = getBoardFree.getViews();
            getBoardFree.setViews(++views);
            boardFreeService.saveBoardFree(getBoardFree);
        }

        List<BoardImage> images = getBoardFree.getGalleries();
        List<Gallery> galleries = null;

        if (Objects.nonNull(images)) {
            List<String> ids = new ArrayList<>();

            images.forEach(gallery -> ids.add(gallery.getId()));
            galleries = galleryService.findByIds(ids);
        }

        BoardCategory boardCategory = boardDAO.getBoardCategory(getBoardFree.getCategory().name(), commonService.getLanguageCode(LocaleContextHolder.getLocale(), null));

        BoardFreeOfMinimum prevPost = boardDAO.getBoardFreeById(new ObjectId(getBoardFree.getId())
                , boardFree.get().getCategory(), Sort.Direction.ASC);
        BoardFreeOfMinimum nextPost = boardDAO.getBoardFreeById(new ObjectId(getBoardFree.getId())
                , boardFree.get().getCategory(), Sort.Direction.DESC);

        FreePostOnDetail post = new FreePostOnDetail(getBoardFree);
        post.setCategory(boardCategory);
        post.setGalleries(galleries);

        return FreePostOnDetailResponse.builder()
                .post(post)
                .prevPost(prevPost)
                .nextPost(nextPost)
                .build();
    }

    @ApiOperation(value = "자유게시판 말머리 목록", produces = "application/json", response = FreeCategoriesResponse.class)
    @RequestMapping(value = "/free/categories", method = RequestMethod.GET)
    public FreeCategoriesResponse getFreeCategories() {

        List<BoardCategory> categories = boardDAO.getBoardCategories(commonService.getLanguageCode(LocaleContextHolder.getLocale(), null));
        return FreeCategoriesResponse.builder()
                .categories(categories)
                .build();
    }

    @ApiOperation(value = "자유게시판 글쓰기", produces = "application/json", response = FreePostOnWriteResponse.class)
    @RequestMapping(value = "/free", method = RequestMethod.POST)
    public FreePostOnWriteResponse addFreePost(@Valid @RequestBody FreePostForm form,
                                               HttpServletRequest request) {

        if (! commonService.isUser())
            throw new ServiceException(ServiceError.UNAUTHORIZED_ACCESS);

        Optional<BoardCategory> boardCategory = boardCategoryService.findOneByCode(form.getCategoryCode().name());

        if (!boardCategory.isPresent())
            throw new ServiceException(ServiceError.CATEGORY_NOT_FOUND);

        List<GalleryOnBoard> galleries = new ArrayList<>();

        if (Objects.nonNull(form.getGalleries())) {
            galleries = form.getGalleries().stream()
                    .map(gallery -> new GalleryOnBoard(gallery.getId(), gallery.getName(), gallery.getFileName(), gallery.getSize()))
                    .collect(Collectors.toList());
        }

        Device device = DeviceUtils.getCurrentDevice(request);

        Integer boardSeq = boardFreeService.insertFreePost(form.getSubject().trim(), form.getContent().trim(), form.getCategoryCode(),
                galleries, commonService.getDeviceInfo(device));

        return FreePostOnWriteResponse.builder()
                .seq(boardSeq)
                .build();
    }

    @ApiOperation(value = "자유게시판 글 고치기", produces = "application/json", response = FreePostOnWriteResponse.class)
    @RequestMapping(value = "/free/{seq}", method = RequestMethod.PUT)
    public FreePostOnWriteResponse editFreePost(@PathVariable Integer seq,
                                                @Valid @RequestBody FreePostForm form,
                                                HttpServletRequest request) {

        if (! commonService.isUser())
            throw new ServiceException(ServiceError.UNAUTHORIZED_ACCESS);

        Optional<BoardCategory> boardCategory = boardCategoryService.findOneByCode(form.getCategoryCode().name());

        if (!boardCategory.isPresent())
            throw new ServiceException(ServiceError.CATEGORY_NOT_FOUND);

        List<GalleryOnBoard> galleries = new ArrayList<>();

        if (Objects.nonNull(form.getGalleries())) {
            galleries = form.getGalleries().stream()
                    .map(gallery -> new GalleryOnBoard(gallery.getId(), gallery.getName(), gallery.getFileName(), gallery.getSize()))
                    .collect(Collectors.toList());
        }

        Device device = DeviceUtils.getCurrentDevice(request);

        Integer boardSeq = boardFreeService.updateFreePost(seq, form.getSubject().trim(), form.getContent().trim(),
                form.getCategoryCode(), galleries, commonService.getDeviceInfo(device));

        return FreePostOnWriteResponse.builder()
                .seq(boardSeq)
                .build();
    }

    @ApiOperation(value = "자유게시판 글 지움", produces = "application/json", response = FreePostOnDeleteResponse.class)
    @RequestMapping(value = "/free/{seq}", method = RequestMethod.DELETE)
    public FreePostOnDeleteResponse deleteFree(@PathVariable Integer seq) {

        if (! commonService.isUser())
            throw new ServiceException(ServiceError.UNAUTHORIZED_ACCESS);

		CommonConst.BOARD_DELETE_TYPE deleteType = boardFreeService.deleteFreePost(seq);

        return FreePostOnDeleteResponse.builder().result(deleteType).build();
    }

    @ApiOperation(value = "자유게시판 글의 댓글 목록")
    @RequestMapping(value = "/free/comments/{seq}", method = RequestMethod.GET)
    public BoardCommentsResponse getFreeComments(@PathVariable Integer seq,
                                             @RequestParam(required = false) String commentId) {

        BoardFreeOfMinimum boardFreeOnComment = boardFreeService.findBoardFreeOfMinimumBySeq(seq);

        List<BoardFreeComment> comments = boardFreeService.getFreeComments(seq, commentId);

        BoardItem boardItem = new BoardItem(boardFreeOnComment.getId(), boardFreeOnComment.getSeq());

        Integer count = boardFreeService.countCommentsByBoardItem(boardItem);

        BoardCommentsResponse response = new BoardCommentsResponse();
        response.setComments(comments);
        response.setCount(count);

        return response;
    }

    @ApiOperation(value = "자유게시판 글의 댓글 달기", produces = "application/json", response = BoardFreeComment.class)
    @RequestMapping(value ="/free/comment", method = RequestMethod.POST)
    public BoardFreeComment addFreeComment(@Valid @RequestBody BoardCommentForm commentRequest,
                                         HttpServletRequest request) {

          if (! commonService.isUser())
            throw new ServiceException(ServiceError.UNAUTHORIZED_ACCESS);

        Device device = DeviceUtils.getCurrentDevice(request);

        return boardFreeService.addFreeComment(commentRequest.getSeq(), commentRequest.getContent().trim(), commonService.getDeviceInfo(device));
    }

    @ApiOperation(value = "글 감정 표현", produces = "application/json", response = UserFeelingResponse.class)
    @RequestMapping(value = "/free/{seq}/{feeling}", method = RequestMethod.POST)
    public UserFeelingResponse addFreeFeeling(@PathVariable Integer seq,
                                              @PathVariable CommonConst.FEELING_TYPE feeling) {

        if (! commonService.isUser())
            throw new ServiceException(ServiceError.UNAUTHORIZED_ACCESS);

        BoardFree boardFree = boardFreeService.setFreeFeelings(seq, feeling);

        Integer numberOfLike = Objects.nonNull(boardFree.getUsersLiking()) ? boardFree.getUsersLiking().size() : 0;
        Integer numberOfDisLike = Objects.nonNull(boardFree.getUsersDisliking()) ? boardFree.getUsersDisliking().size() : 0;

        return UserFeelingResponse.builder()
                .feeling(feeling)
                .numberOfLike(numberOfLike)
                .numberOfDislike(numberOfDisLike)
                .build();
    }

    @ApiOperation(value = "댓글 감정 표현", produces = "application/json", response = UserFeelingResponse.class)
    @RequestMapping(value = "/comment/{commentId}/{feeling}", method = RequestMethod.POST)
    public UserFeelingResponse addFreeCommentFeeling(@PathVariable String commentId,
                                                     @PathVariable CommonConst.FEELING_TYPE feeling) {

        if (! commonService.isUser())
            throw new ServiceException(ServiceError.UNAUTHORIZED_ACCESS);

        BoardFreeComment boardFreeComment = boardFreeService.setFreeCommentFeeling(commentId, feeling);

        Integer numberOfLike = Objects.nonNull(boardFreeComment.getUsersLiking()) ? boardFreeComment.getUsersLiking().size() : 0;
        Integer numberOfDisLike = Objects.nonNull(boardFreeComment.getUsersDisliking()) ? boardFreeComment.getUsersDisliking().size() : 0;

        return UserFeelingResponse.builder()
                .feeling(feeling)
                .numberOfLike(numberOfLike)
                .numberOfDislike(numberOfDisLike)
                .build();
    }
}