package io.github.jdbctemplatemapper.exception;
/**
 * Generic MapperException
 * 
 * @author ajoseph
 *
 */
public class MapperException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public MapperException(String message) {
		super(message);
	}
}
