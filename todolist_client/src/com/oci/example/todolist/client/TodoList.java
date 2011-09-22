package com.oci.example.todolist.client;

import com.google.gwt.cell.client.Cell;
import com.google.gwt.cell.client.CheckboxCell;
import com.google.gwt.cell.client.ClickableTextCell;
import com.google.gwt.cell.client.FieldUpdater;
import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.dom.client.Style;
import com.google.gwt.event.dom.client.*;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.http.client.*;
import com.google.gwt.i18n.client.DateTimeFormat;
import com.google.gwt.user.cellview.client.CellTable;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.HasKeyboardSelectionPolicy;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.*;
import com.google.gwt.view.client.ListDataProvider;
import com.google.gwt.view.client.NoSelectionModel;
import com.google.gwt.view.client.ProvidesKey;

import java.util.*;


/**
 * Entry point classes define <code>onModuleLoad()</code>
 */
@SuppressWarnings({"GWTStyleCheck"})
public class TodoList implements EntryPoint {

    private static final String TODOLIST_BASE_URL = "http://" + Window.Location.getHost() + "/todolist/";
    private static final String ENTRY_URL = TODOLIST_BASE_URL + "entries/";
    private static final String ENTRY_LIST_URL = TODOLIST_BASE_URL + "entries?";


    private static final ProvidesKey<TodoListEntry> todoListEntryKeyProvider = new ProvidesKey<TodoListEntry>() {
        public Object getKey(TodoListEntry entry) {
            return entry.getId();
        }
    };

    Map<Integer, TodoListEntry> todolistEntryMap = new HashMap<Integer, TodoListEntry>();
    private final NoSelectionModel<TodoListEntry> noEntrySelectionModel = new NoSelectionModel<TodoListEntry>();
    private final ListDataProvider<TodoListEntry> todoListDataProvider = new ListDataProvider<TodoListEntry>();

    private final String HeaderText= "Cloud Todo List";
    private final String newEntryHelpText = "Add a new entry";

    private final VerticalPanel mainPanel = new VerticalPanel();
    private final Label header = new Label(HeaderText);
    private final VerticalPanel todoListPanel = new VerticalPanel();
    private final CellTable<TodoListEntry> todoList = new CellTable<TodoListEntry>(todoListEntryKeyProvider);
    private final HorizontalPanel toolPanel = new HorizontalPanel();
    private final Button refreshListButton = new Button("Refresh");
    private final Button clearCompletedButton = new Button("Clear Completed");
    private final TextBox newEntry = new TextBox();
    private final InlineLabel statusLabel = new InlineLabel("");

    private double lastSyncTime=0;
    private static int REFRESH_INTERVAL = 3000;

    public void onModuleLoad() {

        todoList.setKeyboardSelectionPolicy(HasKeyboardSelectionPolicy.KeyboardSelectionPolicy.ENABLED);
        todoList.setSelectionModel(noEntrySelectionModel);

        Column<TodoListEntry, Boolean> completeColumn = addColumn(
                new Column<TodoListEntry, Boolean>(new CheckboxCell()) {
                    @Override
                    public Boolean getValue(TodoListEntry entry) {
                        return entry.isComplete();
                    }
                },
                new FieldUpdater<TodoListEntry, Boolean>() {
                    public void update(int index, TodoListEntry entry, Boolean value) {
                        String urlString = ENTRY_URL + entry.getId() + "?complete=" + (value ? '1' : '0');
                        sendRequest(urlString, RequestBuilder.PUT, new EntryResponseHandler());
                    }
                }
        );

        Column<TodoListEntry, String> titleColumn = addColumn(
                new Column<TodoListEntry, String>(new ClickableTextCell()) {
                    @Override
                    public String getValue(TodoListEntry entry) {
                        return entry.getTitle();
                    }

                    @Override
                    public String getCellStyleNames(Cell.Context context, TodoListEntry entry) {
                        String styles = "todoListText";
                        if (entry.isComplete())
                            styles += " todoListTextComplete";

                        return styles;
                    }
                },
                new FieldUpdater<TodoListEntry, String>() {
                    @Override
                    public void update(int index, TodoListEntry entry, String value) {
                        showEntryInfoDialogBox(entry);
                    }
                }
        );

        todoListDataProvider.addDataDisplay(todoList);

        newEntry.addValueChangeHandler(new ValueChangeHandler<String>() {
            @Override
            public void onValueChange(ValueChangeEvent<String> stringValueChangeEvent) {
                String newTitle = stringValueChangeEvent.getValue();
                newEntry.setText("");
                String urlString = ENTRY_LIST_URL + "title=" + newTitle;
                sendRequest(urlString, RequestBuilder.POST, new EntryResponseHandler());
            }
        });

        newEntry.addFocusHandler(new FocusHandler() {
            @Override
            public void onFocus(FocusEvent event) {
                newEntry.removeStyleName("newEntryHelpText");
                newEntry.setText("");
            }
        });

        newEntry.addBlurHandler(new BlurHandler() {
            @Override
            public void onBlur(BlurEvent event) {
                newEntry.addStyleName("newEntryHelpText");
                newEntry.setText(newEntryHelpText);
            }
        });


        // Listen for mouse events on the Add button.
        refreshListButton.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                refreshTodoListEntries();
            }
        });

        // Listen for mouse events on the clear completed
        clearCompletedButton.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                String urlString = ENTRY_LIST_URL;
                List<Integer> entryIdList = new ArrayList<Integer>();
                for (TodoListEntry entry : todoListDataProvider.getList()) {
                    if (entry.isComplete()) {
                        if (entryIdList.size() == 0) {
                            urlString += "id=" + Integer.toString(entry.getId());
                        } else {
                            urlString += "+" + Integer.toString(entry.getId());
                        }
                        entryIdList.add(entry.getId());
                    }
                }

                if (entryIdList.size() > 0) {
                    sendRequest(urlString, RequestBuilder.DELETE, new DeleteEntryResponseHandler(entryIdList));
                }
            }
        });

        header.setStyleName("todoListHeader");

        toolPanel.setSpacing(4);
        //toolPanel.add(refreshListButton);
        toolPanel.add(clearCompletedButton);

        todoList.setColumnWidth(completeColumn, 45.0, Style.Unit.PX);
        todoList.setColumnWidth(titleColumn, 100.0, Style.Unit.PCT);
        todoList.setStyleName("todoList");
        todoList.addColumnStyleName(0,"todoListColumn");
        todoList.addColumnStyleName(1,"todoListColumn");

        newEntry.setStyleName("newEntry");
        newEntry.addStyleName("newEntryHelpText");
        newEntry.setText(newEntryHelpText);

        todoListPanel.setStyleName("todoListPanel");
        todoListPanel.setHorizontalAlignment(HasHorizontalAlignment.ALIGN_CENTER);
        todoListPanel.add(todoList);
        todoListPanel.add(newEntry);
        todoListPanel.setHorizontalAlignment(HasHorizontalAlignment.ALIGN_LEFT);
        todoListPanel.add(toolPanel);

        mainPanel.setStyleName("mainPanel");
        mainPanel.setHorizontalAlignment(HasHorizontalAlignment.ALIGN_CENTER);
        mainPanel.add(header);
        mainPanel.add(todoListPanel);
        mainPanel.add(statusLabel);

        refreshTodoListEntries();

        // Add it to the root panel.
        RootPanel.get("todoList").add(mainPanel);

        // Setup timer to refresh list automatically.
        Timer refreshTimer = new Timer() {
            @Override
            public void run() {
                refreshTodoListEntries();
            }
        };

        refreshTimer.scheduleRepeating(REFRESH_INTERVAL);
    }

    private void refreshTodoListDisplay() {
        List<TodoListEntry> data = new ArrayList<TodoListEntry>(todolistEntryMap.values());
        Collections.sort(data, new TodoListEntry.CompareCreated());
        todoListDataProvider.setList(data);
        todoListDataProvider.refresh();
    }

    private void showEntryInfoDialogBox(final TodoListEntry entry) {
        final DialogBox dialogBox = new DialogBox();
        dialogBox.setText("Entry");

        // Enable animation.
        dialogBox.setAnimationEnabled(true);

        // Enable glass background.
        dialogBox.setGlassEnabled(true);
        dialogBox.setModal(true);

        VerticalPanel dialogContents = new VerticalPanel();
        dialogContents.setSpacing(4);


        dialogContents.add(new InlineLabel("Title:"));
        final TextBox titleText = new TextBox();
        titleText.setText(entry.getTitle());
        dialogContents.add(titleText);

        dialogContents.add(new InlineLabel("Notes:"));
        final TextArea notesText = new TextArea();
        notesText.setVisibleLines(10);
        notesText.setCharacterWidth(40);
        notesText.setText(entry.getNotes());
        dialogContents.add(notesText);

        final Date created = new Date ((long)(entry.getCreated()*1000));
        final Date modified = new Date((long)(entry.getModified()*1000));
        final DateTimeFormat dateTimeFmt = DateTimeFormat.getFormat(DateTimeFormat.PredefinedFormat.DATE_TIME_SHORT);

        final InlineLabel createdLabel = new InlineLabel("Created: "+ dateTimeFmt.format(created));
        createdLabel.setStyleName("entryDateText");
        dialogContents.add(createdLabel);

        final InlineLabel modifiedLabel = new InlineLabel("Last Modified: " + dateTimeFmt.format(modified));
        modifiedLabel.setStyleName("entryDateText");
        dialogContents.add(modifiedLabel);



        HorizontalPanel buttonPanel = new HorizontalPanel();

        // Add OK Button
        Button okButton = new Button("OK", new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                String newTitle = titleText.getText();
                String newNotes = notesText.getText();

                if (!newTitle.equals(entry.getTitle())
                        || !newNotes.equals(entry.getNotes())) {
                    String urlString = ENTRY_URL + entry.getId()
                            + "?title" + (newTitle.equals("") ? "" : ("=" + newTitle))
                            + ";notes" + (newNotes.equals("") ? "" : ("=" + newNotes));
                    sendRequest(urlString, RequestBuilder.PUT, new EntryResponseHandler());
                }

                dialogBox.hide();
            }
        });

        Button cancelButton = new Button("Cancel", new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                dialogBox.hide();
            }
        });

        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);
        buttonPanel.setSpacing(5);
        dialogContents.add(buttonPanel);

        dialogBox.setWidget(dialogContents);
        dialogContents.setCellHorizontalAlignment(buttonPanel, HasHorizontalAlignment.ALIGN_LEFT);

        dialogBox.center();
    }


    private <T> Column<TodoListEntry, T> addColumn(Column<TodoListEntry, T> column,
                                                   FieldUpdater<TodoListEntry, T> fieldUpdater) {
        column.setFieldUpdater(fieldUpdater);
        todoList.addColumn(column);
        return column;
    }

    private void refreshTodoListEntries() {
        String URL= ENTRY_LIST_URL;
        if (lastSyncTime != 0)
            URL+="modified="+lastSyncTime;
        sendRequest(URL, RequestBuilder.GET, new EntryListResponseHandler());
    }

    class ResponseHandler implements RequestCallback {

        @Override
        public void onError(Request request, Throwable e) {
            statusLabel.setText("Server Error: " + e.toString());
        }

        @Override
        public void onResponseReceived(Request request, Response response) {
            int status_code = response.getStatusCode();
            if ((status_code >= 400) && (status_code < 500)) {
                statusLabel.setText("Client Error: '" + response.getStatusCode() + "' "
                        + response.getStatusText());
            } else if ((status_code >= 500) && (status_code < 600)) {
                if (response.getStatusCode() > 400) {
                    statusLabel.setText("Server Error: '" + response.getStatusCode() + "' "
                            + response.getStatusText());
                }
            }
        }

        native TodoListEntryList parseTodoListEntryList(String json) /*-{
            eval('var res = ' + json);
            return res;
        }-*/;

        native TodoListEntry parseTodoListEntry(String json) /*-{
            eval('var res = ' + json);
            return res;
        }-*/;
    }

    class EntryResponseHandler extends ResponseHandler {
        @Override
        public void onResponseReceived(Request request, Response response) {
            switch (response.getStatusCode()) {

                case 200:
                case 201:
                    TodoListEntry entry = parseTodoListEntry(response.getText());
                    todolistEntryMap.put(entry.getId(), entry);
                    refreshTodoListDisplay();

                default:
                    super.onResponseReceived(request, response);
            }
        }
    }

    class EntryListResponseHandler extends ResponseHandler {
        @Override
        public void onResponseReceived(Request request, Response response) {
            switch (response.getStatusCode()) {

                case 200:
                    TodoListEntryList entryList = parseTodoListEntryList(response.getText());
                    lastSyncTime = entryList.getTimeStamp();
                    JsArray<TodoListEntry> entries = entryList.getEntries();
                    for (int i = 0; i < entries.length(); i++) {
                        TodoListEntry entry = entries.get(i);
                        if (entry.getDeleted())
                            todolistEntryMap.remove(entry.getId());
                        else
                            todolistEntryMap.put(entry.getId(), entry);
                    }
                    refreshTodoListDisplay();

                default:
                    super.onResponseReceived(request, response);
            }
        }
    }

    class DeleteEntryResponseHandler extends ResponseHandler {

        List<Integer> entryIdList = null;

        public DeleteEntryResponseHandler(List<Integer> entryIdList) {
            this.entryIdList = entryIdList;
        }

        @Override
        public void onResponseReceived(Request request, Response response) {
            switch (response.getStatusCode()) {

                case 200:
                    for (int id : entryIdList) {
                        todolistEntryMap.remove(id);
                    }
                    refreshTodoListDisplay();

                default:
                    super.onResponseReceived(request, response);
            }
        }
    }

    private void sendRequest(String url, RequestBuilder.Method httpMethod, ResponseHandler handler) {
        RequestBuilder builder = new RequestBuilder(httpMethod, URL.encode(url));
        try {
            builder.sendRequest(null, handler);
        } catch (RequestException e) {
            statusLabel.setText("Server Error: " + e.toString());
        }
    }
}
