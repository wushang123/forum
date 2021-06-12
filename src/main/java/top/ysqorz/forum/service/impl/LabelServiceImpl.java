package top.ysqorz.forum.service.impl;

import org.apache.ibatis.session.RowBounds;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import tk.mybatis.mapper.entity.Example;
import top.ysqorz.forum.dao.LabelMapper;
import top.ysqorz.forum.po.Label;
import top.ysqorz.forum.service.LabelService;

import javax.annotation.Resource;
import java.util.List;

/**
 * @author passerbyYSQ
 * @create 2021-05-25 11:26
 */
@Service
public class LabelServiceImpl implements LabelService {

    @Resource
    private LabelMapper labelMapper;

    @Override
    public List<Label> getLabelsByPostId(Integer postId) {
        return labelMapper.selectLabelsByPostId(postId);
    }

    @Override
    public List<Label> getLabelsLikeName(String labelName, Integer maxCount) {
        Example example = new Example(Label.class);
        if (!ObjectUtils.isEmpty(labelName)) {
            example.createCriteria().andLike("labelName", "%" + labelName + "%");
        }
        return labelMapper.selectByExampleAndRowBounds(example, new RowBounds(0, maxCount));
    }

    @Override
    public Label getLabelByName(String labelName) {
        Example example = new Example(Label.class);
        example.createCriteria().andEqualTo("labelName", labelName); // labelName是唯一键
        return labelMapper.selectOneByExample(example);
    }

    @Override
    public Label addLabel(String labelName) {
        Label label = new Label();
        label.setLabelName(labelName)
                .setDescription("")
                .setPostCount(1);
        labelMapper.insertUseGeneratedKeys(label); // 自动填充自增长主键
        return label;
    }
}
