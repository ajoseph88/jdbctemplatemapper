package io.github.jdbctemplatemapper.querymerge;

/**
 * interface with the next methods in the chain
 *
 * @author ajoseph
 * @param <T> the type
 */
public interface IQueryMergeHasMany<T> {
  IQueryMergeJoinColumnManySide<T> joinColumnManySide(String joinColumnManySide);

  IQueryMergeThroughJoinTable<T> throughJoinTable(String tableName);
}
