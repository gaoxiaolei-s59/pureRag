package org.puregxl.site.user.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.puregxl.site.user.dao.entity.UserDO;

@Mapper
public interface UserMapper extends BaseMapper<UserDO> {
}
