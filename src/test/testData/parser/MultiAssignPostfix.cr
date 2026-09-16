# Multi-assign with postfix modifier (`location.cr` `self.lines`).
# `start, finish = finish, start if finish < start` needs postfix `if` on
# multi_assignment (single assignment already had it).
class Location
  def self.lines(start, finish)
    return unless start && finish && start.filename == finish.filename
    start, finish = finish, start if finish < start

    finish.line_number - start.line_number + 1
  end
end
