package top.ysqorz.forum.service;

import top.ysqorz.forum.dto.FirstCommentDTO;
import top.ysqorz.forum.dto.PageData;
import top.ysqorz.forum.dto.SecondCommentDTO;
import top.ysqorz.forum.po.FirstComment;
import top.ysqorz.forum.po.Post;
import top.ysqorz.forum.po.SecondComment;

/**
 * @author passerbyYSQ
 * @create 2021-06-20 16:41
 */
public interface CommentService {

    // 发布一级评论
    void publishFirstComment(Post post, String content);

    // 发布二级评论
    void publishSecondComment(FirstComment firstComment, SecondComment quoteComment,
                              String content);

    /**
     * 某个帖子的一级评论列表
     * @param page      当前页
     * @param count     每一页显示的记录数
     * @param isTimeAsc 是否按照发表时间升序
     */
    PageData<FirstCommentDTO> getFirstCommentList(Post post, Integer page, Integer count, Boolean isTimeAsc);

    FirstComment getFirstCommentById(Integer firstCommentId);

    // 二级评论列表
    PageData<SecondCommentDTO> getSecondCommentList(FirstComment firstComment, Integer page, Integer count);

    SecondComment getSecondCommentById(Integer secondCommentId);

    int addSecondCommentCount(Integer firstCommentId, Integer dif);
}
