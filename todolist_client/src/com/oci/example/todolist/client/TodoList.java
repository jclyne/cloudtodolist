package com.oci.example.todolist.client;

import com.google.gwt.cell.client.*;
import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.cellview.client.*;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.view.client.ProvidesKey;
import com.google.gwt.view.client.SingleSelectionModel;

import java.util.List;


/**
 * Entry point classes define <code>onModuleLoad()</code>
 */
public class TodoList implements EntryPoint {

    /**
     * The key provider that allows us to identify Contacts even if a field
     * changes. We identify contacts by their unique ID.
     */
    private static final ProvidesKey<TodoListEntry> todoListEntryKeyProvider = new ProvidesKey<TodoListEntry>() {
        public Object getKey(TodoListEntry entry) {
            return entry.getId();
        }
    };

    private final SingleSelectionModel<TodoListEntry> singleEntrySelectionModel = new SingleSelectionModel<TodoListEntry>();

    private final VerticalPanel mainPanel = new VerticalPanel();
    private final CellTable<TodoListEntry> todoList = new CellTable<TodoListEntry>(todoListEntryKeyProvider);
    private final CellTable<TodoListEntry> newEntry = new CellTable<TodoListEntry>();
    private final HorizontalPanel toolPanel = new HorizontalPanel();
    private final Button refreshListButton = new Button("Refresh");
    private final Button clearCompletedButton = new Button("Clear Completed");

    private TodoListDataClient client;
    private static final int POLL_INTERVAL = 5000;

    public void onModuleLoad() {
        client = new TodoListDataClient(new ClientRequestCallback() {

            @Override
            public void onSuccess() {
                redrawTable();
            }

            @Override
            public void onError(String errStr) {

            }
        });

        todoList.setKeyboardSelectionPolicy(HasKeyboardSelectionPolicy.KeyboardSelectionPolicy.ENABLED);
        todoList.setSelectionModel(singleEntrySelectionModel);
        todoList.addColumn(buildCheckCompleteColumn(),new TextHeader(""));


        Header<String> newEntryFooter=
                new Header<String>(new TextInputCell()) {
                    @Override
                    public String getValue() {
                        return "";
                    }
                };

        newEntryFooter.setUpdater(new ValueUpdater<String>() {
            public void update(String value) {
                client.createNewEntry(value);
            }
        });

        todoList.addColumn(buildEntryTitleColumn(),new TextHeader("Todo List"),newEntryFooter);

        // Listen for mouse events on the Add button.
        refreshListButton.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                client.refreshTodoListEntries();
            }
        });

        // Listen for mouse events on the clear completed
        clearCompletedButton.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                client.clearCompletedEntries();
            }
        });

        toolPanel.add(refreshListButton);
        toolPanel.add(clearCompletedButton);

        mainPanel.add(todoList);
        mainPanel.add(toolPanel);

        // Add it to the root panel.
        RootPanel.get().add(mainPanel);

        client.refreshTodoListEntries();
        //startUpdatePollingTimer();
    }

    private void redrawTable() {
        List<TodoListEntry> entryData = client.getCurrentTodoListEntries();

        todoList.setRowCount(entryData.size(), true);
        todoList.setRowData(0, entryData);
        todoList.redraw();
    }

    private void startUpdatePollingTimer() {
        // Setup timer to refresh list automatically.
        Timer refreshTimer = new Timer() {
            @Override
            public void run() {
                client.refreshTodoListEntries();
                redrawTable();
            }
        };
        refreshTimer.scheduleRepeating(POLL_INTERVAL);
    }

    private Column<TodoListEntry, ?> buildCheckCompleteColumn() {
        // Create the column, mapping a TodoListEntry to the boolean
        Column<TodoListEntry, Boolean> column =
                new Column<TodoListEntry, Boolean>(new CheckboxCell()) {
                    @Override
                    public Boolean getValue(TodoListEntry entry) {
                        return entry.isComplete();
                    }
                };

        // Add a field updater to be notified when the user clears an item
        column.setFieldUpdater(new FieldUpdater<TodoListEntry, Boolean>() {
            public void update(int index, TodoListEntry entry, Boolean value) {
                client.setEntryComplete(entry.getId(), value);
            }
        });

        return column;

    }

    private Column<TodoListEntry, ?> buildEntryTitleColumn() {
        // Create a modifiable title cell
        Column<TodoListEntry, String> column =
                new Column<TodoListEntry, String>(new TextInputCell()) {

                    @Override
                    public String getValue(TodoListEntry entry) {
                        return entry.getTitle();
                    }
                };

        // Add a field updater to be notified when the user enters a new name.
        column.setFieldUpdater(new FieldUpdater<TodoListEntry, String>() {
            public void update(int index, TodoListEntry entry, String value) {
                client.setEntryTitle(entry.getId(), value);
            }
        });

        return column;
    }
}
