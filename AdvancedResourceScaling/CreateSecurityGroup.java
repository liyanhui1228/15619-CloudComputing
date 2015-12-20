import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.AuthorizeSecurityGroupIngressRequest;
import com.amazonaws.services.ec2.model.CreateSecurityGroupRequest;
import com.amazonaws.services.ec2.model.CreateSecurityGroupResult;
import com.amazonaws.services.ec2.model.IpPermission;

public class CreateSecurityGroup {
	static AmazonEC2      ec2;
	static String SGID = null;
	private static void init() throws Exception {
        AWSCredentials credentials = null;
        try {
            credentials = new ProfileCredentialsProvider("default").getCredentials();
        } catch (Exception e) {
            throw new AmazonClientException(
                    "Cannot load the credentials from the credential profiles file. " +
                    "Please make sure that your credentials file is at the correct " +
                    "location (/Users/baiyuanchen/.aws/credentials), and is in valid format.",
                    e);
        }
        ec2 = new AmazonEC2Client(credentials);
    }
	
	
	public void createSecurityGroup() throws Exception {
	init();
	CreateSecurityGroupRequest csgr = new CreateSecurityGroupRequest();
	
    csgr.withGroupName("ALLPORTS").withDescription("ALLPORTS");
    
    CreateSecurityGroupResult createSecurityGroupResult = 
    		  ec2.createSecurityGroup(csgr);
    
    IpPermission ipPermission = 
		new IpPermission();
	    	
	ipPermission.withIpRanges("0.0.0.0/0")  //set Ip permission
	            .withIpProtocol("-1")
	            .withFromPort(0)
	            .withToPort(65535);
	
	AuthorizeSecurityGroupIngressRequest authorizeSecurityGroupIngressRequest =
			new AuthorizeSecurityGroupIngressRequest();
	
	authorizeSecurityGroupIngressRequest.withGroupName("ALLPORTS")   //set ip
		                                    .withIpPermissions(ipPermission);
	
	
	ec2.authorizeSecurityGroupIngress(authorizeSecurityGroupIngressRequest); //set inbound
	//ec2.authorizeSecurityGroupEgress(authorizeSecurityGroupEgressRequest);
	
	SGID = createSecurityGroupResult.getGroupId();
	//System.out.println("Security Group Created");
	System.out.println(SGID);
}
}
