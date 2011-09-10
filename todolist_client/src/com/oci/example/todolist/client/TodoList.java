package com.oci.example.todolist.client;

import com.google.gwt.cell.client.*;
import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.core.client.JsArrayNumber;
import com.google.gwt.dom.client.Style;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.http.client.*;
import com.google.gwt.user.cellview.client.CellTable;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.HasKeyboardSelectionPolicy;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.*;
import com.google.gwt.view.client.ListDataProvider;
import com.google.gwt.view.client.NoSelectionModel;
import com.google.gwt.view.client.ProvidesKey;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 * Entry point classes define <code>onModuleLoad()</code>
 */
public class TodoList implements EntryPoint {

    private static final String TODOLIST_BASE_URL = "http://" + Window.Location.getHost() + "/todolist/api/";
    private static final String GET_ENTRY_URL = TODOLIST_BASE_URL + "getentry?";
    private static final String SET_ENTRY_URL = TODOLIST_BASE_URL + "setentry?";
    private static final String DEL_ENTRY_URL = TODOLIST_BASE_URL + "delentry?";

    private static final ProvidesKey<TodoListEntry> todoListEntryKeyProvider = new ProvidesKey<TodoListEntry>() {
        public Object getKey(TodoListEntry entry) {
            return entry.getId();
        }
    };

    private final NoSelectionModel<TodoListEntry> noEntrySelectionModel = new NoSelectionModel<TodoListEntry>();
    private final ListDataProvider<TodoListEntry> todoListDataProvider = new ListDataProvider<TodoListEntry>();

    private final VerticalPanel mainPanel = new VerticalPanel();
    private final CellTable<TodoListEntry> todoList = new CellTable<TodoListEntry>(todoListEntryKeyProvider);
    private final HorizontalPanel toolPanel = new HorizontalPanel();
    private final Button refreshListButton = new Button("Refresh");
    private final Button clearCompletedButton = new Button("Clear Completed");
    private final TextBox newEntry = new TextBox();
    private final InlineLabel statusLabel = new InlineLabel("");

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
                        entry.setComplete(value);
                        todoList.redraw();

                        String urlString = SET_ENTRY_URL + "id=" + entry.getId() + ";complete=" + (value ? '1' : '0');
                        sendRequest(urlString, RequestBuilder.PUT, new RequestHandler() {
                            @Override
                            public void onSuccess(Request request, Response response) {
                            }
                        });
                    }
                }
        );

        List<String> actions = new ArrayList<String>();
        actions.add("notes");
        actions.add("toggle complete");

        Column<TodoListEntry, String> titleColumn = addColumn(
                new Column<TodoListEntry, String>(new ClickableTextCell()) {
                    @Override
                    public String getValue(TodoListEntry entry) {
                        return entry.getTitle();
                    }

                    @Override
                    public String getCellStyleNames(Cell.Context context, TodoListEntry entry) {
                        String styles = "todo-list-text";
                        if (entry.isComplete())
                            styles+=" todo-list-complete-text";

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

        todoList.setWidth("75%", true);
        todoList.setColumnWidth(completeColumn, 45.0, Style.Unit.PX);
        todoList.setColumnWidth(titleColumn, 100.0, Style.Unit.PCT);

        todoListDataProvider.addDataDisplay(todoList);

        newEntry.addValueChangeHandler(new ValueChangeHandler<String>() {
            @Override
            public void onValueChange(ValueChangeEvent<String> stringValueChangeEvent) {
                String newTitle = stringValueChangeEvent.getValue();
                newEntry.setText("");
                String urlString = SET_ENTRY_URL + "title=" + newTitle;
                sendRequest(urlString, RequestBuilder.POST, new RequestHandler() {
                    @Override
                    public void onSuccess(Request request, Response response) {
                        @SuppressWarnings({"MismatchedQueryAndUpdateOfCollection"})
                        List<TodoListEntry> todoListEntries = todoListDataProvider.getList();
                        JsArray<TodoListEntryResponse> entries = parseSetEntryResponse(response.getText());
                        for (int i = 0; i < entries.length(); i++) {
                            TodoListEntryResponse entry = entries.get(i);
                            todoListEntries.add(new TodoListEntry(entry.getId(),
                                    entry.getTitle(),
                                    entry.getNotes(),
                                    entry.isComplete()));
                        }
                    }
                });
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
                String urlString = DEL_ENTRY_URL;
                int count = 0;
                for (TodoListEntry entry : todoListDataProvider.getList()) {
                    if (entry.isComplete()) {
                        if (count++ == 0) {
                            urlString += "id=" + Integer.toString(entry.getId());
                        } else {
                            urlString += "+" + Integer.toString(entry.getId());
                        }
                    }
                }

                if (count > 0) {
                    sendRequest(urlString, RequestBuilder.PUT, new RequestHandler() {
                        @Override
                        public void onSuccess(Request request, Response response) {
                            List<TodoListEntry> todoListEntries = todoListDataProvider.getList();
                            JsArrayNumber deletedEntries = parseDeleteEntryResponse(response.getText());
                            for (int i = 0; i < deletedEntries.length(); i++) {
                                for (int j = 0; j < todoListEntries.size(); j++) {
                                    if (todoListEntries.get(j).getId() == (int) deletedEntries.get(i)) {
                                        todoListEntries.remove(j);
                                        break;
                                    }
                                }
                            }
                            todoListDataProvider.refresh();
                        }
                    });
                }
            }
        });

        toolPanel.add(refreshListButton);
        toolPanel.add(clearCompletedButton);

        mainPanel.setHorizontalAlignment(HasHorizontalAlignment.ALIGN_LEFT);
        mainPanel.add(todoList);
        mainPanel.add(newEntry);
        mainPanel.add(toolPanel);
        mainPanel.add(statusLabel);

        refreshTodoListEntries();

        // Add it to the root panel.
        RootPanel.get("todoList").add(mainPanel);

        newEntry.setFocus(true);


    }

    @SuppressWarnings({"UnusedDeclaration"})
    final static class TodoListEntryResponse extends JavaScriptObject {
        protected TodoListEntryResponse() {
        }

        public final native int getId() /*-{
            return this.id;
        }-*/;

        public final native String getTitle() /*-{
            return this.title;
        }-*/;

        public final native String getNotes() /*-{
            return this.notes;
        }-*/;

        public final native boolean isComplete() /*-{
            return this.complete == 1;
        }-*/;
    }


    private void refreshTodoListEntries() {
        sendRequest(GET_ENTRY_URL, RequestBuilder.GET, new RequestHandler() {
            @Override
            public void onSuccess(Request request, Response response) {
                JsArray<TodoListEntryResponse> entries = parseSetEntryResponse(response.getText());
                List<TodoListEntry> newDisplayList = new ArrayList<TodoListEntry>();
                for (int i = 0; i < entries.length(); i++) {
                    TodoListEntryResponse entry = entries.get(i);
                    newDisplayList.add(new TodoListEntry(entry.getId(),
                            entry.getTitle(),
                            entry.getNotes(),
                            entry.isComplete()));
                }
                Collections.sort(newDisplayList, new TodoListEntry.CompareId());
                todoListDataProvider.setList(newDisplayList);
                todoListDataProvider.refresh();
            }
        });
    }

    private void showEntryInfoDialogBox(final TodoListEntry entry){
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
        notesText.setVisibleLines(5);
        notesText.setText(entry.getNotes());
        dialogContents.add(notesText);

        dialogBox.setWidget(dialogContents);
        // Add OK Button
        Button okButton = new Button("OK", new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                String newTitle = titleText.getText();
                String newNotes = notesText.getText();

                boolean modified= false;

                String urlString = SET_ENTRY_URL + "id=" + entry.getId();
                if (!newTitle.equals(entry.getTitle())) {
                    modified=true;
                    entry.setTitle(newTitle);
                    urlString+=";title";
                    if (!newTitle.equals("")) urlString+="="+newTitle;
                }

                if (!newNotes.equals(entry.getNotes())) {
                    modified=true;
                    entry.setNotes(newNotes);
                    urlString+=";notes";
                    if (!newNotes.equals("")) urlString+="="+newNotes;
                }

                if (modified) {
                    todoListDataProvider.refresh();
                    sendRequest(urlString, RequestBuilder.PUT, new RequestHandler() {
                        @Override
                        public void onSuccess(Request request, Response response) {
                        }
                    });
                }

                dialogBox.hide();
            }
        });

        dialogContents.add(okButton);
        dialogContents.setCellHorizontalAlignment(okButton, HasHorizontalAlignment.ALIGN_RIGHT);

        dialogBox.center();
    }

    private <T> Column<TodoListEntry, T> addColumn(Column<TodoListEntry, T> column,
                                                   FieldUpdater<TodoListEntry, T> fieldUpdater) {
        column.setFieldUpdater(fieldUpdater);
        todoList.addColumn(column, "");
        return column;
    }

    private <T> Column<TodoListEntry, T> addColumn(Column<TodoListEntry, T> column,
                                                   FieldUpdater<TodoListEntry, T> fieldUpdater,
                                                   String label) {
        column.setFieldUpdater(fieldUpdater);
        todoList.addColumn(column, label);
        return column;
    }

    abstract class RequestHandler implements RequestCallback {

        abstract public void onSuccess(Request request, Response response);

        @Override
        public void onError(Request request, Throwable e) {
            statusLabel.setText("Server Error: " + e.toString());
        }

        @Override
        public void onResponseReceived(Request request, Response response) {
            statusLabel.setText("");
            if (response.getStatusCode() == 200) {
                onSuccess(request, response);
            } else {
                statusLabel.setText("Server Error: '" + response.getStatusCode() + "' "
                        + response.getStatusText());
            }
        }

        native JsArray<TodoListEntryResponse> parseSetEntryResponse(String json) /*-{
            return eval(json);
        }-*/;

        native JsArrayNumber parseDeleteEntryResponse(String json) /*-{
            return eval(json);
        }-*/;
    }

    private void sendRequest(String url, RequestBuilder.Method httpMethod, RequestHandler handler) {
        RequestBuilder builder = new RequestBuilder(httpMethod, URL.encode(url));
        try {
            builder.sendRequest(null, handler);
        } catch (RequestException e) {
            statusLabel.setText("Server Error: " + e.toString());
        }
    }
}
