package com.robonav.app.adapters;

import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.robonav.app.R;
import com.robonav.app.models.Robot;
import com.robonav.app.models.Task;

import java.util.ArrayList;
import java.util.List;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import java.util.Objects;

public class TaskAdapter extends RecyclerView.Adapter<TaskAdapter.TaskViewHolder> {

    private final Context context;
    private final List<Task> taskList;
    private final List<Robot> robotList;

    public TaskAdapter(Context context, List<Task> taskList, List<Robot> robotList) {
        this.context = context;
        this.taskList = taskList;
        this.robotList = robotList;
    }

    @NonNull
    @Override
    public TaskViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.task_item, parent, false);
        return new TaskViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TaskViewHolder holder, int position) {
        List<Task> orderedTaskList = getOrderedTaskList();  // Get the sorted and ordered task list

        // Handle case when no tasks are available to avoid crashes
        if (orderedTaskList.size() > position) {
            Task task = orderedTaskList.get(position);
            Robot responsibleRobot = getRobotForTask(task);

            holder.taskNameTextView.setText(task.getName());
            holder.taskRobotTextView.setText("Fulfilled By: " + (responsibleRobot != null ? responsibleRobot.getName() : "Unknown Robot"));
            String dateCreated = !Objects.equals(task.getDateCreated(), "null") ? task.getDateCreated() : "Unknown";
            holder.taskStartedTextView.setText("Started: " + dateCreated);

            // Handle task status and icon based on progress
            updateTaskStatus(holder, task);
            // Set click listener to show popup
            holder.itemView.setOnClickListener(view -> showTaskPopup(view, task, responsibleRobot));

        }
    }

    // Utility method to get the ordered task list
    private List<Task> getOrderedTaskList() {
        // Separate completed tasks
        List<Task> completedTasks = new ArrayList<>();
        for (Task task : taskList) {
            if ("2".equals(task.getState())) {
                completedTasks.add(task);
            }
        }

        // Sort completed tasks by dateCreated (newest first)
        completedTasks.sort((task1, task2) -> {
            try {
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                Date date1 = dateFormat.parse(task1.getEnd());
                Date date2 = dateFormat.parse(task2.getEnd());
                return date2.compareTo(date1);  // Sort in descending order
            } catch (ParseException e) {
                return 0;
            }
        });

        // Limit to the 5 most recent completed tasks
        completedTasks = completedTasks.size() > 5 ? completedTasks.subList(0, 5) : completedTasks;

        // Create a new list that includes all tasks, keeping the order of non-completed tasks
        List<Task> orderedTaskList = new ArrayList<>();

        // Add non-completed tasks in the same order as in the original taskList
        for (Task task : taskList) {
            if (!"2".equals(task.getState())) {
                orderedTaskList.add(task);
            }
        }

        // Add sorted completed tasks
        orderedTaskList.addAll(completedTasks);

        return orderedTaskList;
    }

    // Helper method to update task status
    private void updateTaskStatus(TaskViewHolder holder, Task task) {
        if (task.getState().equals("1")) {
            holder.taskProgressTextView.setText("Status: Active");
            holder.taskIconImageView.setImageResource(R.drawable.bot);
            holder.taskIconImageView.setVisibility(View.VISIBLE);
        } else if (task.getState().equals("-1")) {
            holder.taskProgressTextView.setText("Status: Error");
            holder.taskIconImageView.setImageResource(R.drawable.ic_error);
            holder.taskIconImageView.setVisibility(View.VISIBLE);
        } else if (task.getState().equals("0")) {
            holder.taskProgressTextView.setText("Status: Queued");
            holder.taskIconImageView.setImageResource(R.drawable.ic_queue);
            holder.taskIconImageView.setVisibility(View.VISIBLE);
        } else if (task.getState().equals("2")) {
            holder.taskProgressTextView.setText("Status: Complete");
            holder.taskIconImageView.setImageResource(R.drawable.ic_task);
            holder.taskIconImageView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public int getItemCount() {
        // Ensure getItemCount returns the size of the ordered task list
        List<Task> orderedTaskList = getOrderedTaskList();  // Get the sorted and ordered task list
        return orderedTaskList.size();  // Return the correct size
    }

    // Utility function to find the robot responsible for the task
    private Robot getRobotForTask(Task task) {
        for (Robot robot : robotList) {
            if (robot.getId().equals(task.getRobotId())) {
                return robot;
            }
        }
        return null; // Return null if no robot is found
    }

    // ViewHolder class
    static class TaskViewHolder extends RecyclerView.ViewHolder {
        TextView taskStartedTextView;
        TextView taskNameTextView, taskRobotTextView, taskProgressTextView;
        ImageView taskIconImageView;

        public TaskViewHolder(@NonNull View itemView) {
            super(itemView);
            taskNameTextView = itemView.findViewById(R.id.task_name);
            taskRobotTextView = itemView.findViewById(R.id.task_robot);
            taskProgressTextView = itemView.findViewById(R.id.task_progress);
            taskIconImageView = itemView.findViewById(R.id.task_icon);
            taskStartedTextView = itemView.findViewById(R.id.task_start);
        }
    }

    // Show popup method with animations
    private void showTaskPopup(View anchorView, Task task, Robot responsibleRobot) {
        View popupView = LayoutInflater.from(context).inflate(R.layout.task_popup_layout, null);

        PopupWindow popupWindow = new PopupWindow(popupView,
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true);

        // Set content for the popup
        TextView titleView = popupView.findViewById(R.id.popup_title);
        ImageView swipeDownIcon = popupView.findViewById(R.id.swipe_down_icon);

        TextView endTitle = popupView.findViewById(R.id.end_time_title);
        TextView progressStatus = popupView.findViewById(R.id.progress_status);
        TextView endDate = popupView.findViewById(R.id.date_completed);
        TextView responsibleRobotView = popupView.findViewById(R.id.responsible_robot);
        TextView dateStarted = popupView.findViewById(R.id.date_started); // New TextView


        // Bind task and robot data
        titleView.setText(task.getName());
        responsibleRobotView.setText((responsibleRobot != null ? responsibleRobot.getName() : "Unknown"));

        if (!"null".equals(task.getEnd())) {
            endTitle.setVisibility(View.VISIBLE);
            endDate.setText(task.getEnd());
            endDate.setVisibility(View.VISIBLE);
        }
        else{
            endTitle.setVisibility(View.GONE);
            endDate.setVisibility(View.GONE);
        }


        // Check if date is null and set it accordingly
        String dateCreated = !Objects.equals(task.getDateCreated(), "null") ? task.getDateCreated() : "Unknown";
        dateStarted.setText(dateCreated);

        // Handle task status
        if (task.getState().equals("1")) {
            progressStatus.setText("Active");
        } else {
            if (task.getState().equals("-1")) {
                progressStatus.setText("Error");
            } else if (task.getState().equals("0")) {
                progressStatus.setText("Queued");
            }
            else if (task.getState().equals("2")){
                progressStatus.setText("Complete");
            }
        }

        // Handle swipe-down icon click
        swipeDownIcon.setOnClickListener(v -> dismissWithAnimation(popupView, popupWindow));

        // Handle outside touch to dismiss with animation
        popupWindow.setTouchInterceptor((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                if (!isInsideViewBounds(popupView, event)) {
                    dismissWithAnimation(popupView, popupWindow);
                    return true; // Consume the touch event
                }
            }
            return false;
        });

        // Apply slide-up animation
        popupView.startAnimation(AnimationUtils.loadAnimation(context, R.anim.slide_up));

        // Show the popup
        popupWindow.showAtLocation(anchorView, Gravity.BOTTOM, 0, 0);
    }

    private void dismissWithAnimation(View popupView, PopupWindow popupWindow) {
        Animation slideDown = AnimationUtils.loadAnimation(context, R.anim.slide_down);
        slideDown.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                popupWindow.dismiss();
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }
        });
        popupView.startAnimation(slideDown);
    }

    private boolean isInsideViewBounds(View view, MotionEvent event) {
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        float x = event.getRawX();
        float y = event.getRawY();
        return x >= location[0] && x <= location[0] + view.getWidth() &&
                y >= location[1] && y <= location[1] + view.getHeight();
    }
}
