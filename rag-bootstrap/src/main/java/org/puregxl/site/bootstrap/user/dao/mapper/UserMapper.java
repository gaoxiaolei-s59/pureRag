package org.puregxl.site.bootstrap.user.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.puregxl.site.bootstrap.user.dao.entity.UserDO;

@Mapper
public interface UserMapper extends BaseMapper<UserDO> {
}
