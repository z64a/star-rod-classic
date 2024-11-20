package util;

import java.util.Iterator;

public class ArrayIterator<T> implements Iterator<T>
{
	private final T[] array;
	private int iterIndex = 0;

	public ArrayIterator(T[] array)
	{
		this.array = array;
	}

	@Override
	public boolean hasNext()
	{
		return array != null && (iterIndex < array.length);
	}

	@Override
	public T next()
	{
		return array[iterIndex++];
	}

	/**
	 * @return array index of the last element returned by next()
	 */
	public int pos()
	{
		if (array == null)
			throw new IllegalStateException("ArrayIterator has null array!");

		if (iterIndex < 1 || iterIndex > array.length)
			throw new IllegalStateException("Invalid call to set() on ArrayIterator. Pos = " + iterIndex + " / " + array.length);

		return iterIndex - 1;
	}

	/**
	 * @param value set at the array index of the last element returned by next()
	 */
	public void set(T value)
	{
		if (array == null)
			throw new IllegalStateException("ArrayIterator has null array!");

		if (iterIndex < 1 || iterIndex > array.length)
			throw new IllegalStateException("Invalid call to set() on ArrayIterator. Pos = " + iterIndex + " / " + array.length);

		array[iterIndex - 1] = value;
	}
}
