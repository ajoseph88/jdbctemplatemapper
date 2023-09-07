package io.github.jdbctemplatemapper.query;

import java.util.List;

import io.github.jdbctemplatemapper.core.JdbcTemplateMapper;

/**
 * interface with the next methods in the chain
 * 
 * @author ajoseph
 *
 * @param <T> the type
 */
public interface IQueryWhere<T> {
    IQueryOrderBy<T> orderBy(String orderBy);

    List<T> execute(JdbcTemplateMapper jdbcTemplateMapper);
}
