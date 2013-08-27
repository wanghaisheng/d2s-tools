package org.data2semantics.platform.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.security.auth.login.AccountNotFoundException;

import org.data2semantics.platform.core.data.DataType;
import org.data2semantics.platform.core.data.Input;
import org.data2semantics.platform.core.data.InstanceInput;
import org.data2semantics.platform.core.data.InstanceOutput;
import org.data2semantics.platform.core.data.JavaType;
import org.data2semantics.platform.core.data.MultiInput;
import org.data2semantics.platform.core.data.Output;
import org.data2semantics.platform.core.data.RawInput;
import org.data2semantics.platform.core.data.ReferenceInput;
import org.data2semantics.platform.domain.Domain;
import org.data2semantics.platform.util.PlatformUtil;

/**
 * This is a representation of a concrete module, which is annotated.
 * Abstract Module will be generated based on information prescribed in YAML, 
 * and annotation on original source code.
 * 
 * 
 * @author wibisono
 *
 */
public abstract class AbstractModule implements Module
{
	protected String name;
	protected Domain domain;
	protected String source;
	
	// Parent Workflow where this module belongs
	protected Workflow workflow;

	protected Map<String, Input>  inputs = new LinkedHashMap<String, Input>();
	protected Map<String, Output> outputs = new LinkedHashMap<String, Output>();
	
	protected List<ModuleInstance> instances = new ArrayList<ModuleInstance>();
	
	protected boolean instantiated = false;
	
	public AbstractModule(Workflow workflow, Domain domain) {
		this.domain = domain;
		this.workflow = workflow;
	}
	
	public Workflow workflow(){
		return workflow;
	}

	public List<Input> inputs() {
		return new ArrayList<Input>(inputs.values());
	}
	public List<Output> outputs() {
		return new ArrayList<Output>(outputs.values());
	}
	
	public Input input(String name)
	{
		if(! inputs.containsKey(name))
			throw new IllegalArgumentException("Input "+name+" does not exist.");

		return inputs.get(name);
	}

	public Output output(String name)
	{
		if(! outputs.containsKey(name))
			throw new IllegalArgumentException("Output "+name+" does not exist.");

		return outputs.get(name);	
	}
	
	public String name() 
	{
		return name;
	}

    @Override
	public int rank()
	{
		int rank = 0;
		for(Input input : inputs())
			if(input instanceof ReferenceInput)
			{
				int dependencyRank = ((ReferenceInput)input).reference().module().rank();
				rank = Math.max(rank, dependencyRank);
			}else 
				if(input instanceof MultiInput){
					MultiInput mi = (MultiInput) input;
					for(Input i : mi.inputs()){
						if(i instanceof ReferenceInput){
							int dependencyRank = ((ReferenceInput)i).reference().module().rank();
							rank = Math.max(rank, dependencyRank);
						}
					}
				}
		
		return rank + 1; 
	}
	
	public List<ModuleInstance> instances(){
		return Collections.unmodifiableList(instances);
	}
	
	public int repeats()
	{
		return 0;
	}

	

	
	public boolean dependsOn(Module curModule) {
		if(parents().contains(curModule))
			return true;
		
		for(Module p : parents()){
			if(p.dependsOn(curModule))
				return true;
		}
		
		return false;
	}

	private Collection<Module> parents() {
		
		Set<Module> result = new LinkedHashSet<Module>();
		
		for(Input i : inputs()){
			if(i instanceof ReferenceInput){
				result.add(((ReferenceInput) i).reference().module());
			} else
			if(i instanceof MultiInput){
				for(Input ii : ((MultiInput) i).inputs()){
					if(ii instanceof ReferenceInput){
						result.add(((ReferenceInput) ii).reference().module());
					}
				}
			}
		}
		
		return result;
	}

	@Override
	public void instantiate(){

		if(!ready())
			throw new IllegalStateException("Failed to instantiate, because the module is not ready");
		
		if(instantiated)
			throw new IllegalStateException("Module can't be instantiated twice");
		
		Map<String, Object> universe = new LinkedHashMap<String, Object>();
		
		instantiateInputRec(universe,  0);
		instantiated = true;
	
	}

	private void instantiateInputRec( Map<String, Object> universe,  int depth) {
		
			if(depth == inputs().size()){
				
				ModuleInstanceImpl newInstance = new ModuleInstanceImpl(universe);
				
				for(int i=0;i<inputs().size();i++){
					Input inp = inputs().get(i);
					InstanceInput ii = new InstanceInput(this, inp, newInstance, universe.get(name()+"."+inp.name()));
					newInstance.inputs.put(ii.name(), ii);
				}
				
				for(Output original : outputs.values()){
					InstanceOutput instanceOutput = new InstanceOutput( this, original, newInstance);
					newInstance.outputs.put(instanceOutput.name(), instanceOutput);
				}
				
				instances.add(newInstance);
				
				return;
			}
			
			Input curInput = inputs().get(depth);
			
			if(curInput instanceof RawInput){
				
				handleRawInput( universe, depth, curInput);
			
			} else
			if(curInput instanceof ReferenceInput){
				
				handleReferenceInput(universe,  depth, curInput);
			
			} else
			if(curInput instanceof MultiInput){
				
				for(Input curMultiRefInput : ((MultiInput) curInput).inputs()){
					
					if(curMultiRefInput instanceof RawInput){
						
						handleRawInput(universe, depth, curMultiRefInput);
						
					} else
					if(curMultiRefInput instanceof ReferenceInput){
						
						handleReferenceInput(universe, 	depth, curMultiRefInput);
						
					} else
						throw new IllegalArgumentException("Input type not recognized " + curMultiRefInput);
					
				}
				
			}
		
	}

	// Handling raw input, add current value to values, and update universe with current assignment
	private void handleRawInput(Map<String, Object> universe, int depth, Input curInput) {
		
		
		Map<String, Object> nextUniverse = new LinkedHashMap<String, Object>(universe);
		nextUniverse.put(name()+"."+curInput.name(), ((RawInput) curInput).value());
		
		instantiateInputRec(nextUniverse,  depth+1);
	}



	private void handleReferenceInput(Map<String, Object> universe, int depth,
			Input curInput) {
		
		ReferenceInput ri = (ReferenceInput) curInput;
		List<ModuleInstance> parentInstances = ri.reference().module().instances();
		
		for(ModuleInstance mi : parentInstances){
		
			if(mi.withinUniverse(universe) ){
				
					Map<String, Object> nextUniverse = new LinkedHashMap<String, Object>(universe);
					nextUniverse.putAll(mi.universe());
					
					Object value = mi.output(((ReferenceInput) curInput).reference().name()).value();
					
					if(!ri.multiValue()){
					
						nextUniverse = new LinkedHashMap<String, Object>(nextUniverse);
						nextUniverse.put(name()+"."+curInput.name(), value);
						
						instantiateInputRec( nextUniverse,  depth+1);
					
					} else {
							
						for(Object v : (List<Object>)value){
					
							nextUniverse = new LinkedHashMap<String, Object>(nextUniverse);
							nextUniverse.put(name()+"."+curInput.name(), v);
							
							instantiateInputRec( nextUniverse, depth+1);
						}
						
					}
			}
		}
	}

	@Override
	public boolean finished() {
		if(!instantiated())
			return false;
		
		for(ModuleInstance mi : instances){
			if(!mi.state().equals(State.FINISHED))
				return false;
		}
		
		return true;
	}
	
	@Override
	public boolean instantiated(){
		
		return instantiated;
	}
	
	@Override
	public boolean ready(){
		
		for(Input input : inputs())
			if(input instanceof ReferenceInput)
			{
				if(!((ReferenceInput)input).reference().module().finished())
					return false;
			} else 
			if(input instanceof MultiInput){
				MultiInput mi = (MultiInput) input;
				for(Input i : mi.inputs()){
					if(i instanceof ReferenceInput){
						if(!((ReferenceInput)i).reference().module().finished())
							return false;
					}
				}
			}
		return true;
	}
	
	public String source(){
		return source;
	}
	
	public Domain domain(){
		return domain;
	}
	
	private class ModuleInstanceImpl implements ModuleInstance
	{
		protected State state = State.READY;
		protected Map<String, InstanceInput> inputs = new LinkedHashMap<String, InstanceInput>();
		protected Map<String, InstanceOutput> outputs = new LinkedHashMap<String, InstanceOutput>();
		protected Map<String, Object> universe = new LinkedHashMap<String, Object>();
		
		Branch branch;
		
		public ModuleInstanceImpl(Map<String, Object> universe) {
			this.universe = universe;
		}


		public Module module()
		{
			return AbstractModule.this;
		}

		public List<InstanceInput> inputs()
		{
			return new ArrayList<InstanceInput>(inputs.values());
		}

		public List<InstanceOutput> outputs()
		{
			return new ArrayList<InstanceOutput>(outputs.values());
		}
		
		public boolean execute()
		{
			ArrayList<String> errors = new ArrayList<String>();
			Map<String, Object> results = new LinkedHashMap<String, Object>();
			
			boolean success = domain.execute(this, errors, results);
		
			// After execution, set values of output so that it can be referenced later on.
			for(String resultName : results.keySet())
				outputs.get(resultName).setValue(results.get(resultName));
			
			state = success ? State.FINISHED : State.FAILED;
			
			return success;
		}

		public State state()
		{
			return state;
		}
		
		@Override
		public InstanceOutput output(String name) {
			
			if(! outputs.containsKey(name))
				throw new IllegalArgumentException("Output '"+name+"' does not exist (module: "+module().source()+").");
			return outputs.get(name);
		}
		
		@Override
		public InstanceInput input(String name) {
			
			if(! inputs.containsKey(name))
				throw new IllegalArgumentException("Input '"+name+"' does not exist (module: "+module().source()+").");

			return inputs.get(name);
		}
	
		public Map<String, Object> universe(){
			return universe;
		}
		
		@Override
		public boolean withinUniverse(Map <String, Object> otherParentValueMap) {
			
			for(String moduleInputKey : otherParentValueMap.keySet()){
				// Ignoring Different set of module/input
				if(!universe.containsKey(moduleInputKey)) continue;
				
				// If the same module/input key are assigned different value we are on different universe/scope
				if(!universe.get(moduleInputKey).equals(otherParentValueMap.get(moduleInputKey)))
					return false;
			}
			
			return true;
		}

		@Override
		public Branch branch() {
			return null;
		}
	}

	static class BranchImpl implements Branch {
				
				List<Branch> parents 	= new ArrayList<Branch>();
				List<Branch> children 	= new ArrayList<Branch>();
				
				ModuleInstance creationPoint = null;
				
				@Override
				public List<Branch> parents() {
					return Collections.unmodifiableList(parents);
				}
		
				@Override
				public List<Branch> children() {
					return Collections.unmodifiableList(children);
				}
		
				@Override
				public Collection<Branch> ancestors() {
					Set<Branch> result = new LinkedHashSet<Branch>();
					
					result.addAll(parents);
					for(Branch parent : parents)
						result.addAll(parent.ancestors());
					
					return result;
				}
		
				@Override
				public Collection<Branch> descendants() {
					Set<Branch> result = new LinkedHashSet<Branch>();
					
					result.addAll(children);
					for(Branch child : children)
						result.addAll(child.descendants());
					
					return result;
				}
		
				@Override
				public ModuleInstance point() {
					
					return creationPoint;
				} 
				
				
				static Branch createChild(ModuleInstance childPoint, List<BranchImpl> parents){
						
						BranchImpl newBranch = new BranchImpl();
						newBranch.creationPoint = childPoint;
					
						for(BranchImpl p : parents){
							p.children.add(newBranch);
						}
						
						newBranch.parents.addAll(parents);
						
						
						return newBranch;
				}
		
				@Override
				public Collection<Branch> siblings() {
					List<ModuleInstance> instanceSiblings = point().module().instances();
					Set<Branch> result = new HashSet<Branch>();
					for(ModuleInstance mi : instanceSiblings){
						if(mi.branch().equals(this)) continue;
						result.add(mi.branch());
					}
					
					return result;
				}
				
			}
}
