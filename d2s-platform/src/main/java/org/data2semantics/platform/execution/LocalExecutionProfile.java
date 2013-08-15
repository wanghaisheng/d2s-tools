package org.data2semantics.platform.execution;

import java.util.List;

import org.data2semantics.platform.core.Module;
import org.data2semantics.platform.core.ModuleInstance;
import org.data2semantics.platform.core.State;
import org.data2semantics.platform.core.Workflow;
import org.data2semantics.platform.resourcespace.ResourceSpace;


/**
 * Local execution profile will just run the module in the current VM
 * @author wibisono
 *
 */
public class LocalExecutionProfile extends ExecutionProfile {

	@Override
	public void executeModules(List<Module> modules) {
		
		for(Module m : modules){

			if(m.ready()){
				
				// Instances of this module will be created
				// Outputs from previous dependency are also provided here.
				m.instantiate(); 

				
				for(ModuleInstance mi : m.instances()){
	
					System.out.println(" Executing instance of module  : " + mi.module().name());
					System.out.println("    Inputs : "+mi.inputs());
					mi.execute();
					System.out.println("    Outputs : "+mi.outputs());
					
				}
			} else 
				throw new IllegalStateException("Module not ready: " + m.name());
		}
		
		
	}
	
	
	

}
