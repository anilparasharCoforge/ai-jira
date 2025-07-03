package aiPlugin.servlet;



import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.IssueInputParameters;
import com.atlassian.jira.bc.issue.IssueService;
import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.issue.attachment.Attachment;
import org.json.JSONObject;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

import com.fasterxml.jackson.databind.ObjectMapper;

import javax.servlet.http.*;
import javax.servlet.ServletException;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

public class CallApiServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException {

        String issueKey = req.getParameter("issueKey");
        if (issueKey == null || issueKey.isEmpty()) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing issueKey parameter");
            return;
        }

        try {
            // 1️⃣ Get the issue
            MutableIssue issue = ComponentAccessor.getIssueManager().getIssueByCurrentKey(issueKey);
            if (issue == null) {
                resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Issue not found");
                return;
            }
            
            
         // 🔹 Build issue JSON object
            Map<String, Object> issueData = new HashMap<>();
            issueData.put("key", issue.getKey());
            issueData.put("summary", issue.getSummary());
            issueData.put("description", issue.getDescription());
            issueData.put("status", issue.getStatus() != null ? issue.getStatus().getName() : "Unknown");
            
            
         // 🔹 Add attachments
            List<Map<String, String>> attachmentList = new ArrayList<>();
            for (Attachment attachment : ComponentAccessor.getAttachmentManager().getAttachments(issue)) {
                Map<String, String> attachmentInfo = new HashMap<>();
                attachmentInfo.put("filename", attachment.getFilename());
                attachmentInfo.put("url", "/secure/attachment/" + attachment.getId() + "/" + attachment.getFilename());
                attachmentList.add(attachmentInfo);
            }
            
            issueData.put("attachments", attachmentList);
            
         // 🔹 Convert to JSON
            ObjectMapper mapper = new ObjectMapper();
            String jsonPayload = mapper.writeValueAsString(issueData);
            
           

            // 2️⃣ Build API URL using issue info
            String apiUrl = "http://localhost:5000/receive";

            // 3️⃣ Call external API
            URL url = new URL(apiUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);
           
            try (OutputStream os = connection.getOutputStream()) {
                os.write(jsonPayload.getBytes("utf-8"));
            }
            
            int code = connection.getResponseCode();
            

            if (code != 200) {
                resp.sendError(HttpServletResponse.SC_BAD_GATEWAY, "API call failed with code " + code);
                return;
            }

            // 4️⃣ Parse response
            Scanner scanner = new Scanner(connection.getInputStream()).useDelimiter("\\A");
            String response = scanner.hasNext() ? scanner.next() : "";
            JSONObject json = new JSONObject(response);
            String externalData = json.optString("status", "NO_STATUS");

            // 5️⃣ Update custom field in Jira issue
			
			  ApplicationUser user =
			  ComponentAccessor.getJiraAuthenticationContext().getLoggedInUser();
			  CustomField field = ComponentAccessor.getCustomFieldManager()
			  .getCustomFieldObjectByName("hsbc-type");
			  
			  if (field == null) {
			  resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
			  "Custom field 'hsbc-type' not found"); return; }
			  
			 
            IssueService issueService = ComponentAccessor.getIssueService();
            IssueInputParameters params = issueService.newIssueInputParameters();
            params.addCustomFieldValue(field.getId(), externalData);
            
            params.setSummary("Updated Summary");
            params.setDescription("This is a new description.");
            params.setPriorityId("2"); // e.g., 2 = High

            IssueService.UpdateValidationResult validationResult =
                issueService.validateUpdate(user, issue.getId(), params);

            if (validationResult.isValid()) {
                issueService.update(user, validationResult);
                resp.setContentType("text/plain");
                resp.getWriter().write("✅ Issue " + issueKey + " updated with: " + externalData);
            } else {
                resp.sendError(HttpServletResponse.SC_BAD_REQUEST,
                        "Validation failed: " + validationResult.getErrorCollection().toString());
            }

        } catch (Exception e) {
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                "Exception occurred: " + e.getMessage());
        }
    }
}
