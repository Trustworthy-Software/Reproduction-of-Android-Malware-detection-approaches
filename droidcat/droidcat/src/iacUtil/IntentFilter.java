package iacUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

public class IntentFilter {
	String Type; //activity, receiver, service etc.
	String ComponentName;
	ArrayList<String> Action;
	ArrayList<String> Category;
	//Data[0] is the data, the followings are extra ones.
	ArrayList<IntentData> Data;
	
	
	public String Statistics(){
		StringBuffer temp=new StringBuffer();
		temp.append(Integer.toString(Action.size())+"|");
		temp.append(Integer.toString(Category.size())+"|");
		for (IntentData Ind : Data){
			if (Ind!=null){
				temp.append(Ind.toString());
			}
			else temp.append("null");
			temp.append(",");
		}
		return temp.toString();
	}
	
	// Type|Component|action1,action2,action3,|data1,dat;a2,da:/ta3,|category1,category2,
	public String toString(){
		StringBuffer temp=new StringBuffer();
		temp.append(Type+"|");
		temp.append(ComponentName+"|");
		for(String action: Action){
			temp.append(action+",");
		}
		temp.append("|");
		
		for(String cate: Category){
			temp.append(cate+",");
		}
		temp.append("|");

		for (IntentData Ind : Data){
			if (Ind!=null){
				temp.append(Ind.toString());
			}
			else temp.append("null");
			temp.append(",");
		}
		

		return temp.toString();
	}
	public IntentFilter(String intentFilter){
		String [] temp=intentFilter.split("|");
		this.ComponentName=temp[0];
		this.Type=temp[1];
		this.Action=(ArrayList<String>) Arrays.asList(temp[2].split(","));
		this.Category=(ArrayList<String>) Arrays.asList(temp[3].split(","));
		for (String datastring: temp[4].split(",")){
			this.addData(new IntentData(datastring));
		}

	}
	public IntentFilter(){
		Type=new String();
		ComponentName=new String();
		Action=new ArrayList<String>();
		Category=new ArrayList<String>();
		Data=new ArrayList<IntentData>();
	}
	public void setType(String Type){
		this.Type=Type;
	}
	public void setComponentName(String ComponentName){
		this.ComponentName=ComponentName;
	}
	public void addAction(String Action){
		this.Action.add(Action);
	}
	public void addData(IntentData Data){
		this.Data.add(Data);
	}
	public void addCategory(String Category){
		this.Category.add(Category);
	}
	public ArrayList<IntentFilter> splitIntent(){
		ArrayList<IntentFilter> Intentlist=new ArrayList<IntentFilter>();
		if (this.Action.size()==0){
			this.Action.add(null);
		}
		if(this.Data.size()==0){
			this.Data.add(null);
		}
		if(this.Category.size()==0){
			this.Category.add(null);
		}
		
		for (String action:this.Action){
			for (IntentData data: this.Data){
				for (String category: this.Category){
					IntentFilter temp=new IntentFilter();
					temp.setComponentName(this.ComponentName);
					temp.setType(this.Type);
					temp.addAction(action);
					temp.addCategory(category);
					temp.addData(data);
					Intentlist.add(temp);
				}
			}
		}
		return  Intentlist;
	}
	
}
