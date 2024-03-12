package logic_simulator.logic_component;

import myutils.math.IVec2;
import logic_simulator.logic_component.TruthValue;
import static logic_simulator.logic_component.TruthValue.*;

public class Wire extends LogicComponent {
	//a wire is only defined by it's starting and ending points. 
	//it should only be able to connect to other things through it's endpoints. 
	
	//for example, in order to create a T junction, 2 wires might seem intuitive, but we need
	//3 if we want there to be a 3 way connection. 
	
	//wires should have the same behaviour as OR gates, except a wire will transmit a FALSE signal even if
	//the other end is ERROR
	
	public IVec2 e0, e1;
	
	public Wire(IVec2 e0, IVec2 e1) {
		super(2, 2);
		this.e0 = new IVec2(e0);
		this.e1 = new IVec2(e1);
		
		this.setWidth(Math.abs(this.e0.x - this.e1.x));
		this.setHeight(Math.abs(this.e0.y - this.e1.y));
		
		this.setInputOffset(0, this.e0);
		this.setInputOffset(0, this.e1);
		this.setOutputOffset(0, this.e0);
		this.setOutputOffset(0, this.e1);
	}
	
	public static TruthValue calcOutput(TruthValue[] inputs) {
		assert inputs.length == 2;
		if(inputs[0] == TRUE || inputs[1] == TRUE) {
			return TRUE;
		}
		if(inputs[0] == FALSE || inputs[1] == FALSE) {
			return FALSE;
		}
		return ERROR;
	}
	
}
