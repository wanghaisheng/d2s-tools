package org.data2semantics.tools.graphs;

import java.io.File;

import org.junit.Ignore;

public class EdgeIterableTest
{

	@Ignore
	public void test()
	{
		File file = new File("/Users/Peter/Documents/datasets/graphs/epinions/epinions.txt");
		
		long i = 0;
		for(EdgeIterable.Line<Integer, Integer> line : EdgeIterable.integers(file))
		{	
			System.out.println(line);
			i++;
			if(i % 1000000 == 0)
				System.out.println(i);
		}
	}

}
